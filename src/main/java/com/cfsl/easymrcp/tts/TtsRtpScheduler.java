package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import com.cfsl.easymrcp.rtp.NettyRtpSenderV3;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * TTS RTP 发送调度器（分组轮询模式）
 * 设计：N个线程 × M路会话/线程，每个线程独立循环发送
 * 优点：避免全局锁竞争，发送间隔精准，资源消耗适中
 */
@Slf4j
@Component
public class TtsRtpScheduler {

    @Value("${tts.rtp-scheduler.pool-size:20}")  // 默认20个线程
    private int schedulerPoolSize;

    @Value("${tts.rtp-scheduler.send-interval-ms:20}")  // 发送间隔（毫秒），默认20ms
    private int sendIntervalMs;

    @Value("${tts.rtp-scheduler.sessions-per-thread:50}")  // 每个线程负责的会话数，默认50路
    private int sessionsPerThread;

    // 分组存储会话，key=线程索引，value=该线程负责的会话列表
    private final List<List<RtpSendTask>> taskGroups = new ArrayList<>();
    private final List<Thread> schedulerThreads = new ArrayList<>();
    private final AtomicInteger taskCount = new AtomicInteger(0);
    private volatile boolean running = true;

    // 会话ID到任务的映射，用于快速查找和删除
    private final ConcurrentHashMap<String, RtpSendTask> taskMap = new ConcurrentHashMap<>();


    @PostConstruct
    public void init() {
        // 初始化任务分组
        for (int i = 0; i < schedulerPoolSize; i++) {
            taskGroups.add(Collections.synchronizedList(new ArrayList<>()));
        }

        // 启动调度线程
        for (int i = 0; i < schedulerPoolSize; i++) {
            final int groupIndex = i;
            Thread thread = new Thread(() -> runSchedulerLoop(groupIndex),
                    "tts-rtp-scheduler-" + (groupIndex + 1));
            thread.setDaemon(true);
            schedulerThreads.add(thread);
            thread.start();
        }
        log.info("TTS RTP 发送调度器初始化完成，线程数: {}, 每线程会话数: {}",
                schedulerPoolSize, sessionsPerThread);
    }

    public String register(NettyTtsRtpProcessor3 nettyTtsRtpProcessor, Consumer<String> callback) {
        String taskId = UUID.randomUUID().toString();
        RtpSendTask task = new RtpSendTask(taskId, nettyTtsRtpProcessor, callback);

        // 计算分组索引（轮询分配）
        int groupIndex = taskCount.getAndIncrement() % schedulerPoolSize;
        taskGroups.get(groupIndex).add(task);
        taskMap.put(taskId, task);

        log.debug("RTP发送任务已注册，任务ID: {}, 分组索引: {}", taskId, groupIndex);
        return taskId;
    }

    public void cancel(String taskId) {
        RtpSendTask task = taskMap.remove(taskId);
        if (task != null) {
            // 从分组中删除
            for (List<RtpSendTask> group : taskGroups) {
                group.remove(task);
            }
            log.debug("RTP发送任务已取消，任务ID: {}", taskId);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        for (Thread thread : schedulerThreads) {
            thread.interrupt();
        }
        // 等待所有线程结束
        for (Thread thread : schedulerThreads) {
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                log.warn("等待线程结束超时", e);
            }
        }
        log.info("TTS RTP 调度器已关闭");
    }

    private void runSchedulerLoop(int groupIndex) {
        List<RtpSendTask> taskGroup = taskGroups.get(groupIndex);
        final long SEND_INTERVAL_NANOS = sendIntervalMs * 1000_000L;
        // 每个线程独立的"应该醒来的时间"变量，避免线程间竞争
        long shouldWakeUp = System.nanoTime();

        while (running && !Thread.currentThread().isInterrupted()) {
            long startTime = System.nanoTime();
            // 优化-减去上轮时间差，减少线程唤醒延迟
            long timeDifference = startTime - shouldWakeUp;
//            log.info("时间差：{}ms", timeDifference / 1000000.0 );
            try {
                // 发送当前轮的所有任务
                sendAllTasksInGroup(taskGroup);
            } catch (Exception e) {
                log.error("调度线程执行异常，分组索引: {}", groupIndex, e);
            }

            // 计算剩余睡眠时间（send-interval-ms减去发送任务的时间）
            long endTime = System.nanoTime();
            long elapsed = endTime - startTime;
            long sleepNanos = SEND_INTERVAL_NANOS - elapsed - timeDifference;
//            log.info("睡眠时间：{}ms", sleepNanos / 1000000.0 );
            shouldWakeUp = endTime + sleepNanos;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
        log.debug("调度线程已结束，分组索引: {}", groupIndex);
    }

    private void sendAllTasksInGroup(List<RtpSendTask> taskGroup) {
        for (RtpSendTask task : taskGroup) {
            try {
                task.doSendOnce();
            } catch (Exception e) {
                log.error("RTP发送任务执行异常，任务ID: {}", task.getTaskId(), e);
            }
        }
    }

    private static class RtpSendTask {
        private final String taskId;
        private final NettyAudioRingBuffer buffer;
        private final NettyRtpSenderV3 sender;
        private final Consumer<String> callback;
        private final int skipBytesInTheEndPacket;

        RtpSendTask(String taskId,
                    NettyTtsRtpProcessor3 nettyTtsRtpProcessor,
                    Consumer<String> callback) {
            this.taskId = taskId;
            this.buffer = nettyTtsRtpProcessor.getOutputRingBuffer();
            this.sender = nettyTtsRtpProcessor.getSender();
            this.skipBytesInTheEndPacket = nettyTtsRtpProcessor.getSkipBytesInTheEndPacket();
            this.callback = callback;
        }

        String getTaskId() {
            return taskId;
        }

        void doSendOnce() {
            int dataSize = buffer.getSize();
            if (dataSize >= TTSConstant.TTS_PCM_FRAME_BYTES) {
                ByteBuf frame = buffer.read(TTSConstant.TTS_PCM_FRAME_BYTES);
                int result = checkEndFlag(frame);

                sender.sendFrame(frame);
                frame.release();

                handleEndResult(result);
            } else if (dataSize > 0) {
                ByteBuf frame = buffer.read(dataSize);
                int result = checkEndFlag(frame);

                // 填充到完整的一帧，保证 RTP 帧大小一致
                ByteBuf fullFrame = fillToFullFrame(frame, TTSConstant.TTS_PCM_FRAME_BYTES);

                sender.sendFrame(fullFrame);
                frame.release();
                fullFrame.release();

                handleEndResult(result);
            } else {
                sender.sendSilence();
            }
        }

        private int checkEndFlag(ByteBuf frame) {
            int readable = frame.readableBytes();
            if (readable < 2) {
                return 0;
            }

            byte last1 = frame.getByte(readable - 2);
            byte last2 = frame.getByte(readable - 1);

            int result = 0;
            if (last1 == TTSConstant.TTS_END_BYTE && last2 == TTSConstant.TTS_END_BYTE) {
                result = 1;
            } else if (last1 == TTSConstant.TTS_INTERRUPT_BYTE && last2 == TTSConstant.TTS_INTERRUPT_BYTE) {
                result = 2;
            }

            // 把结束标记替换为静音，避免产生杂音
            if (result != 0) {
                frame.setByte(readable - 2, TTSConstant.TTS_SILENCE_BYTE);
                frame.setByte(readable - 1, TTSConstant.TTS_SILENCE_BYTE);
            }

            return result;
        }

        private ByteBuf fillToFullFrame(ByteBuf frame, int fullFrameSize) {
            int actualSize = frame.readableBytes();
            int fillSize = fullFrameSize - actualSize;

            ByteBuf fullFrame = frame.alloc().buffer(fullFrameSize);
            fullFrame.writeBytes(frame);

            // 使用批量操作填充静音字节
            if (fillSize > 0) {
                byte[] silenceBytes = new byte[fillSize];
                Arrays.fill(silenceBytes, TTSConstant.TTS_SILENCE_BYTE);
                fullFrame.writeBytes(silenceBytes);
            }

            return fullFrame;
        }

        private void handleEndResult(int result) {
            if (result == 1) {
                com.cfsl.easymrcp.utils.SipUtils.executeTask(() -> callback.accept("completed"));
            } else if (result == 2) {
                com.cfsl.easymrcp.utils.SipUtils.executeTask(() -> callback.accept("interrupt"));
            }
        }
    }
}

package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import com.cfsl.easymrcp.rtp.NettyRtpSenderV3;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * V3 版本 TTS RTP 发送调度器对照实现。
 * 该类按 80bcfb1c 中的 TtsRtpScheduler 语义保留，
 * 用于对照查看旧版共享发送调度方案，不作为当前主链路实现。
 */
@Slf4j
@Component
public class TtsRtpSchedulerV3 {

    @Value("${tts.rtp-scheduler.pool-size:20}")
    private int schedulerPoolSize;

    @Value("${tts.rtp-scheduler.send-interval-ms:20}")
    private int sendIntervalMs;

    @Value("${tts.rtp-scheduler.sessions-per-thread:50}")
    private int sessionsPerThread;

    private final List<List<RtpSendTask>> taskGroups = new ArrayList<>();
    private final List<Thread> schedulerThreads = new ArrayList<>();
    private final AtomicInteger taskCount = new AtomicInteger(0);
    private volatile boolean running = true;
    private final ConcurrentHashMap<String, RtpSendTask> taskMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (int i = 0; i < schedulerPoolSize; i++) {
            taskGroups.add(Collections.synchronizedList(new ArrayList<>()));
        }

        for (int i = 0; i < schedulerPoolSize; i++) {
            final int groupIndex = i;
            Thread thread = new Thread(() -> runSchedulerLoop(groupIndex),
                    "tts-rtp-scheduler-v3-" + (groupIndex + 1));
            thread.setDaemon(true);
            schedulerThreads.add(thread);
            thread.start();
        }
        log.info("TTS RTP V3 对照调度器初始化完成，线程数: {}, 每线程会话数: {}",
                schedulerPoolSize, sessionsPerThread);
    }

    public String register(NettyTtsRtpProcessor3Legacy processor, Consumer<String> callback) {
        String taskId = UUID.randomUUID().toString();
        RtpSendTask task = new RtpSendTask(taskId, processor, callback);

        int groupIndex = taskCount.getAndIncrement() % schedulerPoolSize;
        taskGroups.get(groupIndex).add(task);
        taskMap.put(taskId, task);

        log.debug("V3 对照 RTP发送任务已注册，任务ID: {}, 分组索引: {}", taskId, groupIndex);
        return taskId;
    }

    public void cancel(String taskId) {
        RtpSendTask task = taskMap.remove(taskId);
        if (task != null) {
            for (List<RtpSendTask> group : taskGroups) {
                group.remove(task);
            }
            log.debug("V3 对照 RTP发送任务已取消，任务ID: {}", taskId);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        for (Thread thread : schedulerThreads) {
            thread.interrupt();
        }
        for (Thread thread : schedulerThreads) {
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                log.warn("等待线程结束超时", e);
            }
        }
        log.info("TTS RTP V3 对照调度器已关闭");
    }

    private void runSchedulerLoop(int groupIndex) {
        List<RtpSendTask> taskGroup = taskGroups.get(groupIndex);
        final long sendIntervalNanos = sendIntervalMs * 1_000_000L;
        long shouldWakeUp = System.nanoTime();

        while (running && !Thread.currentThread().isInterrupted()) {
            long startTime = System.nanoTime();
            long timeDifference = startTime - shouldWakeUp;
            try {
                sendAllTasksInGroup(taskGroup);
            } catch (Exception e) {
                log.error("V3 对照调度线程执行异常，分组索引: {}", groupIndex, e);
            }

            long endTime = System.nanoTime();
            long elapsed = endTime - startTime;
            long sleepNanos = sendIntervalNanos - elapsed - timeDifference;
            shouldWakeUp = endTime + sleepNanos;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
        log.debug("V3 对照调度线程已结束，分组索引: {}", groupIndex);
    }

    private void sendAllTasksInGroup(List<RtpSendTask> taskGroup) {
        for (RtpSendTask task : taskGroup) {
            try {
                task.doSendOnce();
            } catch (Exception e) {
                log.error("V3 对照 RTP发送任务执行异常，任务ID: {}", task.getTaskId(), e);
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
                    NettyTtsRtpProcessor3Legacy processor,
                    Consumer<String> callback) {
            this.taskId = taskId;
            this.buffer = processor.getOutputRingBuffer();
            this.sender = processor.getSender();
            this.skipBytesInTheEndPacket = processor.getSkipBytesInTheEndPacket();
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

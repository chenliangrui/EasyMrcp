package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import com.cfsl.easymrcp.rtp.NettyRtpSenderV3;
import com.cfsl.easymrcp.rtp.NettyRtpSenderV4;
import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
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
 * TTS RTP 发送调度器（分组轮询模式）。
 * 保留共享发送线程框架，同时让 output 的消费语义回归旧版 NettyTtsRtpProcessor 的发送逻辑。
 */
@Slf4j
@Component
public class TtsRtpScheduler {

    @Value("${tts.rtp-scheduler.pool-size:20}")
    private int schedulerPoolSize;

    @Value("${tts.rtp-scheduler.send-interval-ms:20}")
    private int sendIntervalMs;

    @Value("${tts.rtp-scheduler.sessions-per-thread:50}")
    private int sessionsPerThread;

    private final List<List<AbstractRtpSendTask>> taskGroups = new ArrayList<>();
    private final List<Thread> schedulerThreads = new ArrayList<>();
    private final AtomicInteger taskCount = new AtomicInteger(0);
    private volatile boolean running = true;
    private final ConcurrentHashMap<String, AbstractRtpSendTask> taskMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (int i = 0; i < schedulerPoolSize; i++) {
            taskGroups.add(Collections.synchronizedList(new ArrayList<>()));
        }

        for (int i = 0; i < schedulerPoolSize; i++) {
            final int groupIndex = i;
            Thread thread = new Thread(() -> runSchedulerLoop(groupIndex), "tts-rtp-scheduler-" + (groupIndex + 1));
            thread.setDaemon(true);
            schedulerThreads.add(thread);
            thread.start();
        }
        log.info("TTS RTP 发送调度器初始化完成，线程数: {}, 每线程会话数: {}", schedulerPoolSize, sessionsPerThread);
    }

    public String register(NettyTtsRtpProcessor4 processor, Consumer<String> callback) {
        String taskId = UUID.randomUUID().toString();
        AbstractRtpSendTask task = new RtpSendTask(taskId, processor, callback);
        int groupIndex = taskCount.getAndIncrement() % schedulerPoolSize;
        taskGroups.get(groupIndex).add(task);
        taskMap.put(taskId, task);
        log.debug("RTP发送任务已注册，任务ID: {}, 分组索引: {}", taskId, groupIndex);
        return taskId;
    }

    /**
     * 兼容旧的 V3 调用点，避免迁移期间其他路径编译失败。
     */
    public String register(NettyTtsRtpProcessor3 processor, Consumer<String> callback) {
        String taskId = UUID.randomUUID().toString();
        AbstractRtpSendTask task = new LegacyRtpSendTask(taskId, processor, callback);
        int groupIndex = taskCount.getAndIncrement() % schedulerPoolSize;
        taskGroups.get(groupIndex).add(task);
        taskMap.put(taskId, task);
        log.debug("RTP发送任务已注册，任务ID: {}, 分组索引: {}", taskId, groupIndex);
        return taskId;
    }

    public void cancel(String taskId) {
        AbstractRtpSendTask task = taskMap.remove(taskId);
        if (task != null) {
            for (List<AbstractRtpSendTask> group : taskGroups) {
                group.remove(task);
            }
            task.release();
            log.debug("RTP发送任务已取消，任务ID: {}", taskId);
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
        log.info("TTS RTP 调度器已关闭");
    }

    private void runSchedulerLoop(int groupIndex) {
        List<AbstractRtpSendTask> taskGroup = taskGroups.get(groupIndex);
        final long sendIntervalNanos = sendIntervalMs * 1_000_000L;
        long shouldWakeUp = System.nanoTime();

        while (running && !Thread.currentThread().isInterrupted()) {
            long startTime = System.nanoTime();
            long timeDifference = startTime - shouldWakeUp;
            try {
                sendAllTasksInGroup(taskGroup);
            } catch (Exception e) {
                log.error("调度线程执行异常，分组索引: {}", groupIndex, e);
            }

            long endTime = System.nanoTime();
            long elapsed = endTime - startTime;
            long sleepNanos = sendIntervalNanos - elapsed - timeDifference;
            shouldWakeUp = endTime + sleepNanos;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
        log.debug("调度线程已结束，分组索引: {}", groupIndex);
    }

    private void sendAllTasksInGroup(List<AbstractRtpSendTask> taskGroup) {
        for (AbstractRtpSendTask task : taskGroup) {
            try {
                task.doSendOnce();
            } catch (Exception e) {
                log.error("RTP发送任务执行异常，任务ID: {}", task.getTaskId(), e);
            }
        }
    }

    private abstract static class AbstractRtpSendTask {
        private final String taskId;

        protected AbstractRtpSendTask(String taskId) {
            this.taskId = taskId;
        }

        String getTaskId() {
            return taskId;
        }

        abstract void doSendOnce();

        void release() {
        }
    }

    private static class RtpSendTask extends AbstractRtpSendTask {
        private final NettyAudioRingBuffer buffer;
        private final NettyRtpSenderV4 sender;
        private final Consumer<String> callback;
        private boolean sendSilence = true;
        private final ByteBuf silenceData;

        RtpSendTask(String taskId, NettyTtsRtpProcessor4 processor, Consumer<String> callback) {
            super(taskId);
            this.buffer = processor.getOutputRingBuffer();
            this.sender = processor.getSender();
            this.callback = callback;
            this.silenceData = ByteBufAllocator.DEFAULT.buffer(EMConstant.VOIP_SAMPLES_PER_FRAME);
            byte[] silenceBytes = new byte[EMConstant.VOIP_SAMPLES_PER_FRAME];
            Arrays.fill(silenceBytes, TTSConstant.TTS_SILENCE_BYTE);
            this.silenceData.writeBytes(silenceBytes);
        }

        /**
         * 单轮发送逻辑对齐旧版 NettyTtsRtpProcessor.startRtpSender()：
         * 读取 output、剥离结束/打断标记、只发送音频数据，并根据状态决定是否继续发静音。
         */
        @Override
        void doSendOnce() {
            ByteBuf peek = buffer.peek(EMConstant.VOIP_SAMPLES_PER_FRAME * 1000);
            if (peek != null && peek.readableBytes() > 0) {
                sendSilence = false;

                int packageCount = peek.readableBytes() / EMConstant.VOIP_SAMPLES_PER_FRAME;
                int redundantData = peek.readableBytes() % EMConstant.VOIP_SAMPLES_PER_FRAME;

                if (peek.readableBytes() == 1 && peek.getByte(0) == TTSConstant.TTS_END_BYTE) {
                    peek.release();
                    buffer.write(TTSConstant.TTS_END_FLAG.retainedDuplicate());
                    return;
                }

                if (!(peek.getByte(peek.readableBytes() - 2) == TTSConstant.TTS_END_BYTE)
                        && !(peek.getByte(peek.readableBytes() - 1) == TTSConstant.TTS_END_BYTE)
                        && redundantData != 0) {
                    packageCount = packageCount > 1 ? packageCount - 1 : 1;
                } else if (packageCount == 0) {
                    packageCount = 1;
                }

                peek.release();

                ByteBuf payload = buffer.read(EMConstant.VOIP_SAMPLES_PER_FRAME * packageCount);
                if (payload != null && payload.readableBytes() > 0) {
                    boolean hasEndFlag = false;
                    boolean hasInterruptFlag = false;

                    if (payload.readableBytes() >= 2) {
                        byte endByte1 = payload.getByte(payload.readableBytes() - 2);
                        byte endByte2 = payload.getByte(payload.readableBytes() - 1);
                        if (endByte1 == TTSConstant.TTS_END_BYTE && endByte2 == TTSConstant.TTS_END_BYTE) {
                            hasEndFlag = true;
                            payload.writerIndex(payload.writerIndex() - 2);
                        } else if (endByte1 == TTSConstant.TTS_INTERRUPT_BYTE && endByte2 == TTSConstant.TTS_INTERRUPT_BYTE) {
                            hasInterruptFlag = true;
                            payload.writerIndex(payload.writerIndex() - 2);
                        }
                    }

                    if (payload.readableBytes() > 0) {
                        sender.sendFrame(payload);
                    }
                    payload.release();

                    if (hasEndFlag) {
                        sendSilence = true;
                        SipUtils.executeTask(() -> callback.accept("completed"));
                    }
                    if (hasInterruptFlag) {
                        sendSilence = true;
                        SipUtils.executeTask(() -> callback.accept("interrupt"));
                    }
                }
            } else if (sendSilence) {
                sender.sendFrame(silenceData);
            }
        }

        @Override
        void release() {
            silenceData.release();
        }
    }

    private static class LegacyRtpSendTask extends AbstractRtpSendTask {
        private final NettyAudioRingBuffer buffer;
        private final NettyRtpSenderV3 sender;
        private final Consumer<String> callback;

        LegacyRtpSendTask(String taskId, NettyTtsRtpProcessor3 processor, Consumer<String> callback) {
            super(taskId);
            this.buffer = processor.getOutputRingBuffer();
            this.sender = processor.getSender();
            this.callback = callback;
        }

        @Override
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
                SipUtils.executeTask(() -> callback.accept("completed"));
            } else if (result == 2) {
                SipUtils.executeTask(() -> callback.accept("interrupt"));
            }
        }
    }
}

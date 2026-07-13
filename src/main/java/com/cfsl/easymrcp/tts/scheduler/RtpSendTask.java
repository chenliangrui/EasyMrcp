package com.cfsl.easymrcp.tts.scheduler;

import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import com.cfsl.easymrcp.rtp.NettyRtpSender;
import com.cfsl.easymrcp.tts.NettyTtsRtpProcessor;
import com.cfsl.easymrcp.tts.TTSConstant;
import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * RTP 发送任务的具体实现。
 *
 * <p>该类封装单个会话的发送状态，包括：
 * <ul>
 *     <li>output ring buffer 读取</li>
 *     <li>按 frameBytes 取帧 / 补帧</li>
 *     <li>END / INTERRUPT 标记识别与替换</li>
 *     <li>空缓冲时持续补静音</li>
 *     <li>completed / interrupt 回调</li>
 * </ul>
 *
 * <p>它不负责选择在哪个线程运行，只负责“单轮发送一次”的语义。
 */
final class RtpSendTask extends AbstractRtpSendTask {
    private final NettyAudioRingBuffer buffer;
    private final NettyRtpSender sender;
    private final Consumer<String> callback;
    private final int frameBytes;
    private final long taskIntervalNanos;
    /** 当前任务自己的静音帧，避免每轮发送都重复构造。 */
    private final ByteBuf silenceData;
    /** 当前任务下一次允许发送的时间点。 */
    private long nextSendTimeNanos = 0L;

    RtpSendTask(String taskId, NettyTtsRtpProcessor processor, Consumer<String> callback) {
        super(taskId);
        this.buffer = processor.getOutputRingBuffer();
        this.sender = processor.getSender();
        this.callback = callback;
        this.frameBytes = processor.getFrameBytes();
        this.taskIntervalNanos = processor.getSendIntervalMs() * 1_000_000L;
        this.silenceData = ByteBufAllocator.DEFAULT.buffer(frameBytes);
        byte[] silenceBytes = new byte[frameBytes];
        Arrays.fill(silenceBytes, TTSConstant.TTS_SILENCE_BYTE);
        this.silenceData.writeBytes(silenceBytes);
    }

    /**
     * 单轮发送逻辑参考 V3 版本共享发送调度：
     * 每轮只发送一帧，不足一帧时补静音，结束/中断标记替换为静音并异步回调结果。
     * 实际发送节拍由会话级 sendIntervalMs 控制。
     */
    @Override
    void doSendOnce(long nowNanos) {
        if (isCanceled()) {
            return;
        }
        if (nextSendTimeNanos == 0L) {
            nextSendTimeNanos = nowNanos;
        }
        if (nowNanos < nextSendTimeNanos) {
            return;
        }

        int dataSize = buffer.getSize();
        if (dataSize >= frameBytes) {
            ByteBuf frame = buffer.read(frameBytes);
            int result = checkMarker(frame);
            sender.sendFrame(frame);
            frame.release();
            handleResult(result);
            nextSendTimeNanos += taskIntervalNanos;
        } else if (dataSize > 0) {
            ByteBuf frame = buffer.read(dataSize);
            int result = checkMarker(frame);
            ByteBuf fullFrame = fillToFullFrame(frame, frameBytes);
            sender.sendFrame(fullFrame);
            frame.release();
            fullFrame.release();
            handleResult(result);
            nextSendTimeNanos += taskIntervalNanos;
        } else {
            sender.sendFrame(silenceData);
            nextSendTimeNanos += taskIntervalNanos;
        }
    }

    /**
     * 检查帧尾的 END / INTERRUPT 标记，并把标记字节替换成静音，避免发出控制字节噪音。
     */
    private int checkMarker(ByteBuf frame) {
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

    /**
     * 不足一帧时补静音到完整帧长度，保持 RTP 发送节拍稳定。
     */
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

    /**
     * END / INTERRUPT 结果通过异步回调回传上层业务。
     */
    private void handleResult(int result) {
        if (result == 1) {
            SipUtils.executeTask(() -> callback.accept("completed"));
        } else if (result == 2) {
            SipUtils.executeTask(() -> callback.accept("interrupt"));
        }
    }

    @Override
    void release() {
        silenceData.release();
    }
}

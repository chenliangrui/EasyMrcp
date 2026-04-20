package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.AudioCodecUtil;
import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import com.cfsl.easymrcp.rtp.NettyRtpSenderV4;
import com.cfsl.easymrcp.tts.scheduler.TtsProcessScheduler;
import com.cfsl.easymrcp.tts.scheduler.TtsRtpScheduler;
import com.cfsl.easymrcp.utils.SpringUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NettyTtsRtpProcessor4 以 NettyTtsRtpProcessor 为功能基线，
 * 保留旧版 input/output 双缓冲和处理语义，只把处理线程改造成共享调度可调用的 processOnce()。
 */
@Slf4j
public class NettyTtsRtpProcessor4 {
    /** 输入缓冲时长，单位秒。 */
    private static final int TTS_INPUT_BUFFER_SECONDS = 30;
    /** 输出缓冲时长，单位秒。 */
    private static final int TTS_OUTPUT_BUFFER_SECONDS = 30;
    /** TTS 链路统一按 8kHz 采样率组织缓冲。 */
    private static final int SAMPLE_RATE = EMConstant.VOIP_SAMPLE_RATE;

    @Getter
    private final NettyAudioRingBuffer inputRingBuffer;
    @Getter
    private final NettyAudioRingBuffer outputRingBuffer;
    @Getter
    private final NettyRtpSenderV4 sender;

    private final AtomicBoolean stop = new AtomicBoolean(false);
    private TtsProcessScheduler processScheduler;
    private TtsRtpScheduler rtpScheduler;

    @Setter
    private TtsCallback callback;
    @Setter
    private String reSample;
    @Setter
    @Getter
    private int skipBytesInTheEndPacket = 0;
    @Getter
    private final int mediaType;
    @Getter
    private final int frameBytes;
    @Getter
    private final int sendIntervalMs;
    @Getter
    private final boolean encodeRequired;

    /**
     * 为解决 24kHz -> 8kHz 时尾部不对齐带来的噪音问题，沿用旧版固定按 6*n 读取的策略。
     */
    private int receiveTakeBytes = 6 * 1000;

    /** 处理调度任务 ID，接入共享处理调度器后用于取消任务。 */
    private String processTaskId;
    /** 发送调度任务 ID，接入共享发送调度器后用于取消任务。 */
    private String schedulerTaskId;

    public NettyTtsRtpProcessor4(String remoteIp, int remotePort, int mediaType, int frameBytes, int sendIntervalMs) throws Exception {
        this.mediaType = mediaType;
        this.frameBytes = frameBytes;
        this.sendIntervalMs = sendIntervalMs;
        this.encodeRequired = mediaType == AudioCodecUtil.PT_PCMA || mediaType == AudioCodecUtil.PT_PCMU;
        try {
            this.processScheduler = SpringUtils.getBean(TtsProcessScheduler.class);
        } catch (Exception e) {
            this.processScheduler = null;
        }
        try {
            this.rtpScheduler = SpringUtils.getBean(TtsRtpScheduler.class);
        } catch (Exception e) {
            this.rtpScheduler = null;
        }
        ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
        this.inputRingBuffer = new NettyAudioRingBuffer(allocator, SAMPLE_RATE, TTS_INPUT_BUFFER_SECONDS, true);
        this.outputRingBuffer = new NettyAudioRingBuffer(allocator, SAMPLE_RATE, TTS_OUTPUT_BUFFER_SECONDS, true);
        this.sender = new NettyRtpSenderV4(remoteIp, remotePort);
        this.sender.setPayloadType(mediaType);
        this.sender.configureSession(frameBytes, !encodeRequired);
    }

    /**
     * 设置 RTP 通道，供外部在媒体链路建立后注入 Netty Channel。
     */
    public void setRtpChannel(Channel channel) {
        sender.setRtpChannel(channel);
    }

    /**
     * 上游以 byte[] 形式写入原始 PCM 数据，写入 inputRingBuffer，供 processOnce() 后续处理。
     */
    public void putData(byte[] data, int bytesRead) {
        if (data == null || data.length == 0 || bytesRead <= 0) {
            return;
        }
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(bytesRead);
        byteBuf.writeBytes(data, 0, bytesRead);
        inputRingBuffer.write(byteBuf);
        byteBuf.release();
    }

    /**
     * 上游直接写入 ByteBuf 形式的原始 PCM 数据到 inputRingBuffer。
     */
    public void putData(ByteBuf data) {
        if (data == null || data.readableBytes() == 0) {
            return;
        }
        inputRingBuffer.write(data);
    }

    /**
     * 单次推进一轮处理流程：检查 input、必要时裁尾、重采样、按协商编码组织输出，并写入 output。
     * 该方法不阻塞、不自建线程，供共享处理调度器周期性调用。
     */
    public void processOnce() {
        if (stop.get()) {
            return;
        }
        try {
            if (inputRingBuffer.getSize() == 0) {
                return;
            }

            if (inputRingBuffer.getSize() < receiveTakeBytes) {
                ByteBuf peek = inputRingBuffer.peek(receiveTakeBytes);
                try {
                    if (peek == null || peek.readableBytes() < 2) {
                        return;
                    }
                    boolean hasEndFlag = peek.getByte(peek.readableBytes() - 2) == TTSConstant.TTS_END_BYTE
                            && peek.getByte(peek.readableBytes() - 1) == TTSConstant.TTS_END_BYTE;
                    if (!hasEndFlag) {
                        return;
                    }
                    if (skipBytesInTheEndPacket != 0) {
                        skipEndData();
                    }
                } finally {
                    if (peek != null) {
                        peek.release();
                    }
                }
            }

            ByteBuf pcmData = takeDataAsByteBuf(inputRingBuffer, receiveTakeBytes);
            if (pcmData == null || pcmData.readableBytes() == 0) {
                return;
            }

            boolean hasEndFlag = false;
            if (pcmData.readableBytes() >= 2
                    && pcmData.getByte(pcmData.readableBytes() - 2) == TTSConstant.TTS_END_BYTE
                    && pcmData.getByte(pcmData.readableBytes() - 1) == TTSConstant.TTS_END_BYTE) {
                hasEndFlag = true;
                pcmData.writerIndex(pcmData.writerIndex() - 2);
            }

            ByteBuf processedData = pcmData;
            if ("downsample24kTo8k".equals(reSample)) {
                processedData = downsample24kTo8k(pcmData);
            }

            ByteBuf sendPayloadData = encodeRequired ? AudioCodecUtil.encode(processedData, mediaType) : processedData.retainedDuplicate();
            putData(outputRingBuffer, sendPayloadData);
            sendPayloadData.release();

            if (hasEndFlag) {
                putData(outputRingBuffer, TTSConstant.TTS_END_FLAG.retainedDuplicate());
            }

            if (processedData != pcmData) {
                processedData.release();
            }
            pcmData.release();
        } catch (Exception e) {
            log.error("处理音频数据异常", e);
        }
    }

    /**
     * 将 PCM 字节流从 24kHz 降采样到 8kHz，沿用旧版算法语义。
     */
    public static ByteBuf downsample24kTo8k(ByteBuf input) {
        if (input == null || input.readableBytes() == 0) {
            return ByteBufAllocator.DEFAULT.buffer(0);
        }

        int sampleSize = 2;
        int ratio = 3;
        int totalSamples = input.readableBytes() / sampleSize;
        int newSamples = totalSamples / ratio;
        ByteBuf output = ByteBufAllocator.DEFAULT.buffer(newSamples * sampleSize);

        for (int i = 0; i < newSamples; i++) {
            int idx1 = i * ratio * sampleSize;
            int idx2 = idx1 + sampleSize;
            int idx3 = idx2 + sampleSize;
            if (idx3 + 1 >= input.readableBytes()) {
                break;
            }

            int s1 = (input.getByte(idx1 + 1) << 8) | (input.getByte(idx1) & 0xFF);
            int s2 = (input.getByte(idx2 + 1) << 8) | (input.getByte(idx2) & 0xFF);
            int s3 = (input.getByte(idx3 + 1) << 8) | (input.getByte(idx3) & 0xFF);

            int avg = (s1 + s2 + s3) / 3;
            if (avg > Short.MAX_VALUE) {
                avg = Short.MAX_VALUE;
            }
            if (avg < Short.MIN_VALUE) {
                avg = Short.MIN_VALUE;
            }

            output.writeByte((byte) (avg & 0xFF));
            output.writeByte((byte) ((avg >> 8) & 0xFF));
        }

        return output;
    }

    /**
     * 检测到结束标记时，按旧版语义在 inputRingBuffer 中裁掉末尾多余 PCM，并重新补回 end flag。
     */
    private void skipEndData() {
        int totalSize = inputRingBuffer.getSize();
        if (totalSize > skipBytesInTheEndPacket + 2) {
            ByteBuf allData = inputRingBuffer.read(totalSize);
            int keepBytes = allData.readableBytes() - skipBytesInTheEndPacket - 2;
            if (keepBytes > 0) {
                ByteBuf keepData = allData.readSlice(keepBytes);
                inputRingBuffer.write(keepData.retainedDuplicate());
                log.info("检测到结束标志，已去除末尾{}字节，保留{}字节", skipBytesInTheEndPacket, keepBytes);
            }
            inputRingBuffer.write(TTSConstant.TTS_END_FLAG.retainedDuplicate());
            allData.release();
        } else {
            inputRingBuffer.clear();
            inputRingBuffer.write(TTSConstant.TTS_END_FLAG.retainedDuplicate());
            log.info("检测到结束标志，剩余{}字节，剩余数据不足{}字节，已清空", totalSize, skipBytesInTheEndPacket);
        }
    }

    /**
     * 从环形缓冲区读取一段 ByteBuf 数据，保持与旧版相同的读取上限语义。
     */
    private ByteBuf takeDataAsByteBuf(NettyAudioRingBuffer buffer, int maxLength) {
        if (buffer.getSize() == 0) {
            return null;
        }
        int actualLength = Math.min(maxLength, buffer.getSize());
        ByteBuf byteBuf = buffer.read(actualLength);
        if (byteBuf.readableBytes() == 0) {
            byteBuf.release();
            return null;
        }
        return byteBuf;
    }

    /**
     * 将一段 ByteBuf 数据写入指定环形缓冲区，用于复用旧版 output 写入路径。
     */
    private void putData(NettyAudioRingBuffer buffer, ByteBuf data) {
        if (data == null || data.readableBytes() == 0) {
            return;
        }
        buffer.write(data);
    }

    /**
     * 注册共享处理调度任务和共享发送调度任务。
     */
    public void startRtpSender() {
        if (processScheduler == null) {
            processScheduler = SpringUtils.getBean(TtsProcessScheduler.class);
        }
        if (rtpScheduler == null) {
            rtpScheduler = SpringUtils.getBean(TtsRtpScheduler.class);
        }
        if (processTaskId == null) {
            processTaskId = processScheduler.register(this);
        }
        if (schedulerTaskId == null) {
            schedulerTaskId = rtpScheduler.register(this, result -> {
                if (callback != null) {
                    callback.apply(result);
                }
            });
        }
    }

    /**
     * 停止处理器并取消处理/发送调度任务。
     */
    public void stopRtpSender() {
        stop.set(true);
        if (processScheduler != null && processTaskId != null) {
            processScheduler.cancel(processTaskId);
            processTaskId = null;
        }
        if (rtpScheduler != null && schedulerTaskId != null) {
            rtpScheduler.cancel(schedulerTaskId);
            schedulerTaskId = null;
        }
        if (sender != null) {
            sender.close();
        }
        releaseResources();
    }

    /**
     * 沿用旧版 interrupt 语义：同时清 input/output，并把 interrupt 标记写入 output。
     */
    public void interrupt() {
        try {
            inputRingBuffer.clear();
            outputRingBuffer.clear();
            putData(outputRingBuffer, TTSConstant.TTS_INTERRUPT_FLAG.retainedDuplicate());
            log.debug("已中断TTS播放");
        } catch (Exception e) {
            log.error("中断TTS播放时出现异常", e);
        }
    }

    /**
     * 释放 input/output ring buffer 资源。
     */
    public void releaseResources() {
        try {
            if (!inputRingBuffer.isClosed()) {
                inputRingBuffer.release();
            }
            if (!outputRingBuffer.isClosed()) {
                outputRingBuffer.release();
            }
        } catch (Exception e) {
            log.warn("释放缓冲区资源时出现异常", e);
        }
    }
}

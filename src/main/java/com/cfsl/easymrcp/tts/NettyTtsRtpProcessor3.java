package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import com.cfsl.easymrcp.rtp.NettyRtpSenderV3;
import com.cfsl.easymrcp.utils.SpringUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 重构后的 TTS 音频处理器（V3）
 * 极简设计：只负责数据写入和调度注册
 * <p>
 * 使用方式：
 * processor.putData(...)  // 写入数据
 * processor.startRtpSender();  // 注册调度任务
 * processor.stopRtpSender();   // 取消调度任务
 */
@Slf4j
public class NettyTtsRtpProcessor3 {

    @Getter
    private NettyRtpSenderV3 sender;
    @Setter
    private TtsCallback callback;

    // 输出缓冲区配置
    private static final int TTS_OUTPUT_BUFFER_SECONDS = 30;
    private static final int SAMPLE_RATE = EMConstant.VOIP_SAMPLE_RATE;
    @Getter
    private final NettyAudioRingBuffer outputRingBuffer;

    // 调度器任务 ID
    private String schedulerTaskId;
    private final TtsRtpScheduler scheduler;

    /**
     * 需要跳过的末尾的字节数(PCM格式)
     * 例如使用：PCM 8kHz 16bit
     * 那么200ms = 8000 * 2 * 0.2 = 3200字节
     */
    @Setter
    @Getter
    private int skipBytesInTheEndPacket = 0;

    public NettyTtsRtpProcessor3(String remoteIp, int remotePort, int mediaType) {
        this.scheduler = SpringUtils.getBean(TtsRtpScheduler.class);

        // 初始化输出环形缓冲区
        ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
        this.outputRingBuffer = new NettyAudioRingBuffer(allocator, SAMPLE_RATE, TTS_OUTPUT_BUFFER_SECONDS, true);

        try {
            this.sender = new NettyRtpSenderV3(remoteIp, remotePort);
            this.sender.setPayloadType(mediaType);
        } catch (Exception e) {
            log.error("初始化 NettyTtsRtpProcessorV3 失败", e);
            throw new RuntimeException("初始化 RTP 发送器失败", e);
        }
    }

    public void setRtpChannel(Channel channel) {
        sender.setRtpChannel(channel);
    }

    public void putData(byte[] data, int bytesRead) {
        if (data == null || data.length == 0 || bytesRead <= 0) {
            return;
        }
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(bytesRead);
        byteBuf.writeBytes(data, 0, bytesRead);
        outputRingBuffer.write(byteBuf);
        byteBuf.release();
    }

    public void putData(ByteBuf data) {
        if (data == null || data.readableBytes() == 0) {
            return;
        }
        outputRingBuffer.write(data);
    }

    public void writeEndFlag() {
        outputRingBuffer.write(TTSConstant.TTS_END_FLAG.retainedDuplicate());
    }

    public void startRtpSender() {
        if (schedulerTaskId != null) {
            log.warn("RTP 发送器已在运行中，任务ID: {}", schedulerTaskId);
            return;
        }

        schedulerTaskId = scheduler.register(this, result -> {
            log.info("TTS RTP 发送任务完成，结果: {}", result);
            if (callback != null) {
                com.cfsl.easymrcp.utils.SipUtils.executeTask(() -> callback.apply(result));
            }
        });

        log.debug("RTP 发送任务已注册，任务ID: {}", schedulerTaskId);
    }

    public void stopRtpSender() {
        if (schedulerTaskId != null) {
            scheduler.cancel(schedulerTaskId);
            schedulerTaskId = null;
        }

        if (sender != null) {
            sender.close();
        }

        releaseResources();
    }

    public void interrupt() {
        try {
            outputRingBuffer.clear();
            if (sender != null) {
                outputRingBuffer.write(TTSConstant.TTS_INTERRUPT_FLAG.retainedDuplicate());
            }
            log.debug("已中断 TTS 播放");
        } catch (Exception e) {
            log.error("中断 TTS 播放时出现异常", e);
        }
    }

    public void releaseResources() {
        try {
            if (outputRingBuffer != null && !outputRingBuffer.isClosed()) {
                outputRingBuffer.release();
            }
        } catch (Exception e) {
            log.warn("释放缓冲区资源时出现异常", e);
        }
    }

    public String getBufferStatus() {
        return String.format("TTS V3 - 调度任务ID: %s, 缓冲区: %s",
                schedulerTaskId, outputRingBuffer.getStatusInfo());
    }
}

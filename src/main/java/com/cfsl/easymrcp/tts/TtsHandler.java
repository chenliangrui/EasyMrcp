package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.domain.TtsConfig;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.MrcpConnection;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * 管理一通电话中的tts相关的操作
 * 主要侧重于与不同厂家tts客户端的集成，处理音频流、控制tts客户端生命周期等操作
 */
@Slf4j
public abstract class TtsHandler implements MrcpConnection {
    @Getter
    @Setter
    private String channelId;
    @Getter
    @Setter
    private TtsCallback callback;
    @Getter
    boolean stop = false;
    protected String reSample;
    // 音频处理器
    private NettyTtsRtpProcessor rtpProcessor;

    @Override
    public void create(String remoteIp, int remotePort) {
        try {
            // 创建RTP处理器
            rtpProcessor = new NettyTtsRtpProcessor(reSample, remoteIp, remotePort);
            // 启动处理器
            rtpProcessor.startProcessing();
        } catch (Exception e) {
            log.error("初始化TTS失败: {}", e.getMessage(), e);
            throw new RuntimeException("初始化TTS失败", e);
        }
    }

    public void startRtpSender() {
        rtpProcessor.startRtpSender();
    }

    public void setRtpChannel(Channel channel) {
        rtpProcessor.setRtpChannel(channel);
    }

    public void setConfig(TtsConfig ttsConfig) {
        if (ttsConfig != null) {
            reSample = ttsConfig.getReSample();
        }
    }

    public void transmit(String text) {
        rtpProcessor.setCallback(callback);
        create();
        speak(text);
    }

    /**
     * TTS播放静音
     * @param duration 静音时长(毫秒ms)
     */
    public void silence(int duration)  {
        rtpProcessor.setCallback(callback);
        int i = duration * 16;
        if (reSample != null && reSample.equals("downsample24kTo8k")) {
            i = i * 3;
        }
        byte[] silenceData = new byte[i];
        Arrays.fill(silenceData, (byte) 0x00);
        putAudioData(silenceData, silenceData.length);
        putAudioData(TTSConstant.TTS_END_FLAG, TTSConstant.TTS_END_FLAG.length);
    }

    @Override
    public void close() {
        stop = true;
        rtpProcessor.stopRtpSender();
        ttsClose();
    }

    /**
     * 向音频处理器中添加数据
     *
     * @param pcmBuffer      音频数据
     * @param bytesRead
     */
    public void putAudioData(byte[] pcmBuffer, int bytesRead) {
        byte[] data = new byte[bytesRead];
        System.arraycopy(pcmBuffer, 0, data, 0, bytesRead);
        rtpProcessor.putData(data);
    }

    /**
     * 中断当前TTS播放
     */
    public void interrupt() {
        if (rtpProcessor != null) {
            rtpProcessor.interrupt();
        }
        log.info("TTS播放已中断");
    }

    public abstract void create();

    public abstract void speak(String text);

    /**
     * 关闭TTS资源
     */
    public abstract void ttsClose();

    public void stop() {
        stop = true;
    }
}

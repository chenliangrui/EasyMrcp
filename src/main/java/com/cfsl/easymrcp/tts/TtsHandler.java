package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.ProcessorCreator;
import com.cfsl.easymrcp.domain.TtsConfig;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.MrcpConnection;
import com.cfsl.easymrcp.utils.SpringUtils;
import io.netty.buffer.ByteBuf;
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
public class TtsHandler implements MrcpConnection {
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

    // tts对接厂商处理器
    @Setter
    @Getter
    private TtsProcessor ttsProcessor;

    @Override
    public void create(String remoteIp, int remotePort) {
        try {
            // 创建RTP处理器
            rtpProcessor = new NettyTtsRtpProcessor(remoteIp, remotePort);
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

    public void setReSample(String reSample) {
        this.reSample = reSample;
        rtpProcessor.setReSample(reSample);
    }

    public void transmit(String id, String text) {
        rtpProcessor.setCallback(callback);
        ProcessorCreator ttsChose = SpringUtils.getBean(ProcessorCreator.class);
        if (ttsProcessor == null) {
            // 懒加载tts引擎，没有参数则使用配置文件中的默认值
            ttsProcessor = ttsChose.getTtsProcessor(id);
        }
        // 设置对接的tts引擎
        TtsEngine ttsEngine = ttsChose.setTtsEngine(id, ttsProcessor);
        ttsProcessor.createAndSpeak(ttsEngine, text);
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
        // 使用ByteBuf版本的结束标记
        rtpProcessor.putData(TTSConstant.TTS_END_FLAG.retainedDuplicate());
    }

    @Override
    public void close() {
        stop = true;
        rtpProcessor.stopRtpSender();
        ttsProcessor.ttsClose();
    }

    /**
     * 向音频处理器中添加数据
     *
     * @param pcmBuffer      音频数据
     * @param bytesRead
     */
    public void putAudioData(byte[] pcmBuffer, int bytesRead) {
        rtpProcessor.putData(pcmBuffer, bytesRead);
    }
    
    /**
     * 向音频处理器中添加ByteBuf数据
     *
     * @param pcmBuffer      音频数据ByteBuf
     */
    public void putAudioData(ByteBuf pcmBuffer) {
        rtpProcessor.putData(pcmBuffer);
    }

    /**
     * 中断当前TTS播放
     */
    public void interrupt() {
        if (rtpProcessor != null) {
            rtpProcessor.interrupt();
        }
        log.debug("TTS播放已中断");
    }

    public void stop() {
        stop = true;
    }
}

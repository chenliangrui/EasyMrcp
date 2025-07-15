package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.domain.TtsConfig;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.RtpConnection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.BindException;
import java.net.DatagramSocket;

@Slf4j
public abstract class TtsHandler implements RtpConnection {
    @Getter
    @Setter
    private String channelId;
    @Getter
    @Setter
    private TtsCallback callback;
    @Getter
    boolean stop = false;
    protected String reSample;

    @Getter
    protected RealTimeAudioProcessor processor;

    @Override
    public void create(String localIp, DatagramSocket localSocket, String remoteIp, int remotePort) {
        //初始化rtp
        processor = new RealTimeAudioProcessor(localSocket, reSample, remoteIp, remotePort);
        create();
        processor.startProcessing();
        processor.startRtpSender();
    }

    public void setConfig(TtsConfig ttsConfig) {
        if (ttsConfig.getReSample() != null && !ttsConfig.getReSample().isEmpty()) {
            this.reSample = ttsConfig.getReSample();
        }
    }

    public void transmit(String text) {
        processor.setCallback(getCallback());
        speak(text);
    }

    @Override
    public void close() {
        processor.stopRtpSender();
        ttsClose();
        getCallback().apply("");
    }

    public abstract void create();

    public abstract void speak(String text);

    public abstract void ttsClose();

    public void stop() {
        stop = true;
    }

}

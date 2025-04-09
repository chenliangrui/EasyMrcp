package com.example.easymrcp.tts;

import com.example.easymrcp.mrcp.AsrCallback;
import com.example.easymrcp.mrcp.TtsCallback;
import com.example.easymrcp.rtp.RtpConnection;
import lombok.Getter;
import lombok.Setter;

public abstract class TtsHandler implements RtpConnection {
    @Getter
    @Setter
    private String channelId;
    @Getter
    @Setter
    private TtsCallback callback;
    boolean stop = false;

    RealTimeAudioProcessor processor;

    @Override
    public void create(String localIp, int localPort, String remoteIp, int remotePort) {
        //初始化rtp
        try {
            processor = new RealTimeAudioProcessor(localPort);
            processor.DEST_IP = remoteIp;
            processor.DEST_PORT = remotePort;
            create();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void transmit(String text) {
        processor.setCallback(getCallback());
        speak(text);
    };

    @Override
    public void close() {
        processor.stopRtpSender();
        ttsClose();
    }

    public abstract void create();

    public abstract void ttsClose();

    public abstract void speak(String text);

    public void stop() {
        stop = true;
    };

}

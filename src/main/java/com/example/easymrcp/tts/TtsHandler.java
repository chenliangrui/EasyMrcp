package com.example.easymrcp.tts;

import com.example.easymrcp.mrcp.Callback;
import com.example.easymrcp.rtp.RtpConnection;

public abstract class TtsHandler implements RtpConnection {
    private String channelId;
    private Callback callback;

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public abstract void transmit(String text);

    public void setCallback(Callback callback) {
        this.callback = callback;
    }
}

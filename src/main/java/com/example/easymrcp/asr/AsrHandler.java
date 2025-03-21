package com.example.easymrcp.asr;

import com.example.easymrcp.rtp.RtpConnection;
import lombok.Data;

@Data
public abstract class AsrHandler implements RtpConnection {
    private String channelId;

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public abstract void receive();

    public abstract String complete();
}

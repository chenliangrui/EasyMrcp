package com.example.easymrcp.rtp;

import lombok.Data;

public interface RtpConnection {
    public abstract void close();
    String getChannelId();
    void setChannelId(String channelId);
}

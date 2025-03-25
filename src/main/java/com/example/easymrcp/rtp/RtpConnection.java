package com.example.easymrcp.rtp;

import lombok.Data;

public interface RtpConnection {
    void create(String ip, int port);
    void close();
    String getChannelId();
    void setChannelId(String channelId);
}

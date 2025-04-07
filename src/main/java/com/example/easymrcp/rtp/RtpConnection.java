package com.example.easymrcp.rtp;

import lombok.Data;

public interface RtpConnection {
    void create(String localIp, int localPort, String remoteIp, int remotePort);
    void close();
    String getChannelId();
    void setChannelId(String channelId);
}

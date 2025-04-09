package com.example.easymrcp.rtp;

public interface RtpConnection {
    void create(String localIp, int localPort, String remoteIp, int remotePort);
    void close();
    String getChannelId();
    void setChannelId(String channelId);
}

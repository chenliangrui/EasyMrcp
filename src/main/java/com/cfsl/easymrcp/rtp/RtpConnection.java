package com.cfsl.easymrcp.rtp;

/**
 * 用于sip协议中对于rtp的管理
 */
public interface RtpConnection {
    /**
     * 打开rtp连接，以及处理asr、tts的初始化过程
     * @param localIp rtp本地ip
     * @param localPort rtp本地端口
     * @param remoteIp rtp远端ip
     * @param remotePort rtp远端端口
     */
    void create(String localIp, int localPort, String remoteIp, int remotePort);

    /**
     * 关闭rtp连接，以及处理asr、tts的关闭过程
     */
    void close();

    String getChannelId();

    void setChannelId(String channelId);
}

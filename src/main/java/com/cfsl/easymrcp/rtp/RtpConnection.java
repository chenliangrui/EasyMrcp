package com.cfsl.easymrcp.rtp;

import java.net.DatagramSocket;

/**
 * 用于sip协议中对于rtp的管理
 */
public interface RtpConnection {
    /**
     * 打开rtp连接，以及处理asr、tts的初始化过程
     *
     * @param localIp    rtp本地ip
     * @param localSocket  rtp本地连接
     * @param remoteIp   rtp远端ip
     * @param remotePort rtp远端端口
     * @return 最终使用端口
     */
    void create(String localIp, DatagramSocket localSocket, String remoteIp, int remotePort);

    /**
     * 关闭rtp连接，以及处理asr、tts的关闭过程
     */
    void close();

    String getChannelId();

    void setChannelId(String channelId);
}

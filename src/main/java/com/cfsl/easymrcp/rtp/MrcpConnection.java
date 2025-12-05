package com.cfsl.easymrcp.rtp;

/**
 * 用于对一通电话中mrcp声明周期的管理
 */
public interface MrcpConnection {
    /**
     * 处理asr、tts的初始化过程
     *
     * @param remoteIp   rtp远端ip
     * @param remotePort rtp远端端口
     */
    void create(String remoteIp, int remotePort);

    /**
     * 处理asr、tts的关闭过程
     */
    void close();
}

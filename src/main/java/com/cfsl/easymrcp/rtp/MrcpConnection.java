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
     * @param mediaType sdp使用的编码
     * @param frameBytes 单个RTP包负载字节数（由SDP协商结果计算）
     * @param sendIntervalMs 发送间隔（由SDP中的ptime计算）
     */
    void create(String remoteIp, int remotePort, int mediaType, int frameBytes, int sendIntervalMs);

    /**
     * 处理asr、tts的关闭过程
     */
    void close();
}

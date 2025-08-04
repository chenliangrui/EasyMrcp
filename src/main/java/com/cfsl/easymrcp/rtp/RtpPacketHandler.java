package com.cfsl.easymrcp.rtp;

/**
 * RTP数据包处理接口
 * 用于处理接收到的RTP数据包
 */
@FunctionalInterface
public interface RtpPacketHandler {
    
    /**
     * 处理RTP数据包
     * 
     * @param data RTP数据包字节数组
     */
    void handlePacket(byte[] data);
} 
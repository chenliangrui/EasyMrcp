package com.cfsl.easymrcp.rtp;

import com.cfsl.easymrcp.common.EMConstant;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

/**
 * 基于Netty实现的RTP发送器
 * 用于替换基于BIO的G711RtpSender实现
 */
@Slf4j
public class NettyRtpSender {
    Channel channel;
    // RTP配置参数
    private static final int RTP_HEADER_SIZE = 12;
    private static final int PAYLOAD_TYPE = 8;  // G.711u的RTP载荷类型
    private long nextSendTime = 0;

    // RTP头部字段
    private int sequenceNumber = 0;
    private int timestamp = 0;
    private final int ssrc = new Random().nextInt(Integer.MAX_VALUE);
    private RtpManager rtpManager;
    private boolean interrupt = false;
    private final InetAddress destAddress;
    private final int destPort;

    /**
     * 构造函数
     *
     * @param destIp         目标IP
     * @param destPort       目标端口
     */
    public NettyRtpSender(String destIp, int destPort) throws UnknownHostException {
        this.destAddress = InetAddress.getByName(destIp);
        this.destPort = destPort;
    }

    /**
     * 发送G.711u音频帧（每帧160字节，对应20ms音频）
     *
     * @param g711Data G711编码的音频数据
     */
    public void sendFrame(byte[] g711Data) {
        int offset = 0;
        InetSocketAddress remoteAddress = new InetSocketAddress(destAddress, destPort);
        while (offset < g711Data.length && !interrupt) {
            int frameSize = Math.min(EMConstant.VOIP_SAMPLES_PER_FRAME, g711Data.length - offset);
            byte[] rtpPacket = buildRtpPacket(g711Data, offset, frameSize);
            DatagramPacket packet = new DatagramPacket(Unpooled.copiedBuffer(rtpPacket), remoteAddress);
            channel.writeAndFlush(packet);
            // 控制发送速率
            long time = System.nanoTime();
            if (nextSendTime == 0) {
                nextSendTime = time;
            }
            long parkTime = time - nextSendTime;
            if (parkTime < -20 * 1000000) {
                LockSupport.parkNanos(20 * 1000000);
            } else {
                LockSupport.parkNanos(-parkTime);
            }

            offset += frameSize;
            updateHeader(); // 更新序列号和时间戳
            nextSendTime += EMConstant.VOIP_FRAME_DURATION * 1000000;
        }
        interrupt = false;
    }

    /**
     * 构建RTP包
     *
     * @param payload 负载数据
     * @param offset  偏移量
     * @param length  长度
     * @return 完整的RTP包
     */
    private byte[] buildRtpPacket(byte[] payload, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(RTP_HEADER_SIZE + length);

        // RTP头部（RFC3550）
        buffer.put((byte) 0x80);  // Version 2, no padding/extension/CSRC
        buffer.put((byte) (PAYLOAD_TYPE & 0x7F));
        buffer.putShort((short) sequenceNumber);
        buffer.putInt(timestamp);
        buffer.putInt(ssrc);

        // 音频负载
        buffer.put(payload, offset, length);

        return buffer.array();
    }

    /**
     * 更新RTP头部字段
     */
    private void updateHeader() {
        sequenceNumber = (sequenceNumber + 1) & 0xFFFF;
        timestamp += EMConstant.VOIP_SAMPLES_PER_FRAME; // 时间戳增量=8000*0.02=160
    }

    /**
     * 中断当前发送
     */
    public void interrupt() {
        interrupt = true;
    }

    public void setRtpChannel(Channel channel) {
        this.channel = channel;
    }

    public void close() {
        if (channel != null) {
            channel.close();
        }
    }
}
package com.cfsl.easymrcp.rtp;

import com.cfsl.easymrcp.common.EMConstant;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;
import io.netty.buffer.ByteBuf;

/**
 * 基于Netty实现的RTP发送器 - L16 PCM专用版本
 * 用于8kHz 16bit PCM音频数据的RTP发送
 */
@Slf4j
public class NettyRtpSender2 {
    Channel channel;
    // RTP配置参数
    private static final int RTP_HEADER_SIZE = 12;
    @Setter
    private int payloadType;  // RTP Payload Type，由外部设置
    private long nextSendTime = 0;

    // RTP头部字段
    private int sequenceNumber = 0;
    private int timestamp = 0;
    private final int ssrc = new Random().nextInt(Integer.MAX_VALUE);
    private boolean interrupt = false;
    private final InetAddress destAddress;
    private final int destPort;

    /**
     * 构造函数
     *
     * @param destIp         目标IP
     * @param destPort       目标端口
     */
    public NettyRtpSender2(String destIp, int destPort) throws UnknownHostException {
        this.destAddress = InetAddress.getByName(destIp);
        this.destPort = destPort;
    }

    /**
     * 发送L16 PCM音频帧（每帧320字节，对应20ms音频，8kHz 16bit）
     *
     * @param pcmData L16 PCM编码的音频数据ByteBuf
     */
    public void sendFrame(ByteBuf pcmData) {
        if (pcmData == null || pcmData.readableBytes() == 0) {
            return;
        }

        InetSocketAddress remoteAddress = new InetSocketAddress(destAddress, destPort);
        int remainingBytes = pcmData.readableBytes();
        int readerIndex = pcmData.readerIndex();

        while (remainingBytes > 0 && !interrupt) {
            // L16 PCM每帧320字节 (8kHz 16bit 20ms)
            int frameSize = Math.min(EMConstant.VOIP_L16_BYTES_PER_FRAME, remainingBytes);

            // 直接使用ByteBuf构建RTP包，避免拷贝
            ByteBuf rtpPacket = buildRtpPacket(pcmData, readerIndex, frameSize);
            DatagramPacket packet = new DatagramPacket(rtpPacket, remoteAddress);
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

            readerIndex += frameSize;
            remainingBytes -= frameSize;
            updateHeader(); // 更新序列号和时间戳
            nextSendTime += EMConstant.VOIP_FRAME_DURATION * 1000000;
        }
        interrupt = false;
    }

    /**
     * 构建RTP包（字节序转换版本）
     *
     * @param payload 负载数据ByteBuf
     * @param offset  偏移量
     * @param length  长度
     * @return 完整的RTP包ByteBuf
     */
    private ByteBuf buildRtpPacket(ByteBuf payload, int offset, int length) {
        ByteBuf rtpPacket = Unpooled.buffer(RTP_HEADER_SIZE + length);

        // RTP头部（RFC3550）
        rtpPacket.writeByte(0x80);  // Version 2, no padding/extension/CSRC
        rtpPacket.writeByte(payloadType & 0x7F);  // 使用动态Payload Type
        rtpPacket.writeShort(sequenceNumber);
        rtpPacket.writeInt(timestamp);
        rtpPacket.writeInt(ssrc);

        // 音频负载 - 转换字节序（小端到网络字节序/大端）
        for (int i = offset; i < offset + length; i += 2) {
            if (i + 1 < offset + length) {
                byte lowByte = payload.getByte(i);
                byte highByte = payload.getByte(i + 1);
                rtpPacket.writeByte(highByte);  // 高字节在前
                rtpPacket.writeByte(lowByte);   // 低字节在后
            }
        }

        return rtpPacket;
    }

    /**
     * 更新RTP头部字段
     * 时间戳增量按采样点数计算 (8kHz 20ms = 160采样点)
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

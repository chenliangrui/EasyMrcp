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
import io.netty.buffer.ByteBuf;

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
     * @param g711Data G711编码的音频数据ByteBuf
     */
    public void sendFrame(ByteBuf g711Data) {
        if (g711Data == null || g711Data.readableBytes() == 0) {
            return;
        }
        
        InetSocketAddress remoteAddress = new InetSocketAddress(destAddress, destPort);
        int remainingBytes = g711Data.readableBytes();
        int readerIndex = g711Data.readerIndex();
        
        while (remainingBytes > 0 && !interrupt) {
            int frameSize = Math.min(EMConstant.VOIP_SAMPLES_PER_FRAME, remainingBytes);
            
            // 直接使用ByteBuf构建RTP包，避免拷贝
            ByteBuf rtpPacket = buildRtpPacket(g711Data, readerIndex, frameSize);
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
     * 构建RTP包（零拷贝版本）
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
        rtpPacket.writeByte(PAYLOAD_TYPE & 0x7F);
        rtpPacket.writeShort(sequenceNumber);
        rtpPacket.writeInt(timestamp);
        rtpPacket.writeInt(ssrc);

        // 音频负载 - 直接复制，避免额外的byte[]转换
        rtpPacket.writeBytes(payload, offset, length);

        return rtpPacket;
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
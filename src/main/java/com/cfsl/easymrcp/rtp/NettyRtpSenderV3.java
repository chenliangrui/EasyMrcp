package com.cfsl.easymrcp.rtp;

import com.cfsl.easymrcp.common.EMConstant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * 重构后的 TTS RTP 发送器（V3）
 * 特点：
 * 1. 完全非阻塞（不使用 LockSupport.parkNanos）
 * 2. 只负责 RTP 包构建和发送
 * 3. 内部维护 RTP 头部状态（序列号、时间戳）
 * 4. 支持发送静音帧
 *
 * 发送流程：
 * 1. 传入 L16 PCM 帧（每帧 320 字节，20ms 音频）
 * 2. 构建 RTP 包（字节序转换）
 * 3. writeAndFlush 到 Netty 通道
 * 4. 更新状态（立即返回）
 */
@Slf4j
public class NettyRtpSenderV3 {
    private static final int RTP_HEADER_SIZE = 12;

    @Setter
    private int payloadType = 96; // 默认使用 L16 动态负载类型
    private int sequenceNumber = new Random().nextInt(65536); // 随机初始序列号
    private int timestamp = new Random().nextInt(0x7FFFFFFF); // 随机初始时间戳
    private final int ssrc = new Random().nextInt(Integer.MAX_VALUE);

    private final InetAddress destAddress;
    private final int destPort;
    private Channel channel;

    // 复用的静音数据（8kHz,16bit,20ms = 320字节）
    private ByteBuf silenceFrame;

    public NettyRtpSenderV3(String destIp, int destPort) throws UnknownHostException {
        this.destAddress = InetAddress.getByName(destIp);
        this.destPort = destPort;
        this.silenceFrame = createSilenceFrame();
    }

    /**
     * 设置 RTP 通道
     */
    public void setRtpChannel(Channel channel) {
        this.channel = channel;
    }

    /**
     * 发送 L16 PCM 音频帧（非阻塞）
     * 每个调用发送一帧（320字节，20ms音频）
     *
     * @param pcmData L16 编码的 PCM 数据（20ms = 320字节）
     */
    public void sendFrame(ByteBuf pcmData) {
        if (pcmData == null || pcmData.readableBytes() == 0) {
            return;
        }

        if (channel == null || !channel.isActive()) {
            log.debug("RTP 通道未准备好");
            return;
        }

        try {
            int frameSize = Math.min(EMConstant.VOIP_L16_BYTES_PER_FRAME, pcmData.readableBytes());
            ByteBuf rtpPacket = buildRtpPacket(pcmData, pcmData.readerIndex(), frameSize);
            DatagramPacket packet = new DatagramPacket(rtpPacket,
                    new InetSocketAddress(destAddress, destPort));
            channel.writeAndFlush(packet);
            updateRtpHeader();
        } catch (Exception e) {
            log.error("RTP 发送失败", e);
        }
    }

    /**
     * 发送静音帧（非阻塞）
     * 优化：直接复用静音帧ByteBuf，避免每次创建新对象
     */
    public void sendSilence() {
        sendFrame(silenceFrame);
    }


    /**
     * 构建 RTP 包
     * L16 字节序转换：Java 是小端，网络是大端
     * 优化：使用批量操作替代逐个字节操作
     */
    private ByteBuf buildRtpPacket(ByteBuf payload, int offset, int length) {
        ByteBuf rtpPacket = Unpooled.buffer(RTP_HEADER_SIZE + length);

        // RTP 头部（RFC 3550）
        rtpPacket.writeByte(0x80); // Version 2, no padding/extension/CSRC
        rtpPacket.writeByte(payloadType & 0x7F); // Payload Type
        rtpPacket.writeShort(sequenceNumber);
        rtpPacket.writeInt(timestamp);
        rtpPacket.writeInt(ssrc);

        // 音频负载 - 批量字节序转换（小端 → 大端）
        byte[] tempBuffer = new byte[length];
        payload.getBytes(offset, tempBuffer);

        // 批量转换字节序
        for (int i = 0; i < tempBuffer.length; i += 2) {
            if (i + 1 < tempBuffer.length) {
                byte lowByte = tempBuffer[i];
                byte highByte = tempBuffer[i + 1];
                tempBuffer[i] = highByte;
                tempBuffer[i + 1] = lowByte;
            }
        }

        rtpPacket.writeBytes(tempBuffer);
        return rtpPacket;
    }

    /**
     * 更新 RTP 头部
     */
    private void updateRtpHeader() {
        sequenceNumber = (sequenceNumber + 1) & 0xFFFF;
        timestamp += EMConstant.VOIP_SAMPLES_PER_FRAME; // 8000Hz * 20ms = 160 samples
    }

    /**
     * 创建静音帧
     */
    private ByteBuf createSilenceFrame() {
        int frameSize = EMConstant.VOIP_L16_BYTES_PER_FRAME;
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(frameSize);
        byte[] silenceBytes = new byte[frameSize];
        for (int i = 0; i < frameSize; i++) {
            silenceBytes[i] = (byte) 0x00; // 0值表示静音
        }
        buffer.writeBytes(silenceBytes);
        return buffer;
    }

    /**
     * 关闭发送器
     */
    public void close() {
        if (silenceFrame != null) {
            try {
                silenceFrame.release();
            } catch (Exception e) {
                log.warn("释放静音帧失败", e);
            }
        }
        if (channel != null) {
            channel.close();
        }
    }
}

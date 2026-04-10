package com.cfsl.easymrcp.rtp;

import com.cfsl.easymrcp.common.EMConstant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * 统一的 TTS RTP 发送器。
 * 保持单 sender 结构不变，但根据 payloadType 适配发送内容：
 * - L16：发送前做小端转大端
 * - G711：直接写入 payload
 */
@Slf4j
public class NettyRtpSenderV4 {
    private static final int RTP_HEADER_SIZE = 12;

    private int payloadType = 96;
    private int sequenceNumber = new Random().nextInt(65536);
    private int timestamp = new Random().nextInt(0x7FFFFFFF);
    private final int ssrc = new Random().nextInt(Integer.MAX_VALUE);

    private final InetAddress destAddress;
    private final int destPort;
    private Channel channel;
    private ByteBuf silenceFrame;

    public NettyRtpSenderV4(String destIp, int destPort) throws UnknownHostException {
        this.destAddress = InetAddress.getByName(destIp);
        this.destPort = destPort;
        this.silenceFrame = createSilenceFrame();
    }

    /**
     * 设置 payloadType，并按当前编码重建静音帧。
     */
    public void setPayloadType(int payloadType) {
        this.payloadType = payloadType;
        if (silenceFrame != null) {
            silenceFrame.release();
        }
        this.silenceFrame = createSilenceFrame();
    }

    /**
     * 设置 RTP 通道。
     */
    public void setRtpChannel(Channel channel) {
        this.channel = channel;
    }

    /**
     * 非阻塞发送一帧音频，由上层调度器负责节拍控制。
     */
    public void sendFrame(ByteBuf payloadData) {
        if (payloadData == null || payloadData.readableBytes() == 0) {
            return;
        }

        if (channel == null || !channel.isActive()) {
            log.debug("RTP 通道未准备好");
            return;
        }

        try {
            ByteBuf rtpPacket = buildRtpPacket(payloadData, payloadData.readerIndex(), payloadData.readableBytes());
            DatagramPacket packet = new DatagramPacket(rtpPacket, new InetSocketAddress(destAddress, destPort));
            channel.writeAndFlush(packet);
            updateRtpHeader();
        } catch (Exception e) {
            log.error("RTP 发送失败", e);
        }
    }

    /**
     * 发送当前编码对应的静音帧。
     */
    public void sendSilence() {
        sendFrame(silenceFrame);
    }

    /**
     * 兼容旧调用点，当前实现无需额外中断状态。
     */
    public void interrupt() {
    }

    /**
     * 构建 RTP 包，L16 需要大小端转换，其它编码直接写 payload。
     */
    private ByteBuf buildRtpPacket(ByteBuf payload, int offset, int length) {
        ByteBuf rtpPacket = Unpooled.buffer(RTP_HEADER_SIZE + length);
        rtpPacket.writeByte(0x80);
        rtpPacket.writeByte(payloadType & 0x7F);
        rtpPacket.writeShort(sequenceNumber);
        rtpPacket.writeInt(timestamp);
        rtpPacket.writeInt(ssrc);

        if (payloadType == 96) {
            byte[] tempBuffer = new byte[length];
            payload.getBytes(offset, tempBuffer);
            for (int i = 0; i < tempBuffer.length; i += 2) {
                if (i + 1 < tempBuffer.length) {
                    byte lowByte = tempBuffer[i];
                    byte highByte = tempBuffer[i + 1];
                    tempBuffer[i] = highByte;
                    tempBuffer[i + 1] = lowByte;
                }
            }
            rtpPacket.writeBytes(tempBuffer);
        } else {
            rtpPacket.writeBytes(payload, offset, length);
        }
        return rtpPacket;
    }

    /**
     * 更新时间戳和序列号，时间戳仍按 20ms@8k 的 160 sample 增量推进。
     */
    private void updateRtpHeader() {
        sequenceNumber = (sequenceNumber + 1) & 0xFFFF;
        timestamp += EMConstant.VOIP_SAMPLES_PER_FRAME;
    }

    /**
     * 按当前 payloadType 创建静音帧。
     */
    private ByteBuf createSilenceFrame() {
        int frameSize = payloadType == 96 ? EMConstant.VOIP_L16_BYTES_PER_FRAME : EMConstant.VOIP_SAMPLES_PER_FRAME;
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(frameSize);
        byte fill = (byte) 0x00;
        for (int i = 0; i < frameSize; i++) {
            buffer.writeByte(fill);
        }
        return buffer;
    }

    /**
     * 关闭发送器并释放静音帧资源。
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

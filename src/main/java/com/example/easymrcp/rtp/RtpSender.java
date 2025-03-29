package com.example.easymrcp.rtp;

import java.net.*;
import javax.sound.sampled.*;

public class RtpSender {
    private static final int RTP_HEADER_SIZE = 12;
    private static final int PAYLOAD_TYPE = 0; // G.711u的payload type
    private static final int CLOCK_RATE = 8000; // 8kHz时钟频率

    private final DatagramSocket socket;
    private final InetAddress destAddress;
    private final int destPort;

    // RTP协议相关参数
    private int sequenceNumber = 0;
    private long timestamp = 0;
    private final long ssrc;

    public RtpSender(String destIp, int destPort) throws SocketException {
        try {
            this.destAddress = InetAddress.getByName(destIp);
            this.destPort = destPort;
            this.socket = new DatagramSocket();
            this.ssrc = (long) (Math.random() * 0xFFFFFFFFL); // 随机生成SSRC
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid destination IP", e);
        }
    }

    public void sendAudioFrame(byte[] g711Data, long timestamp) {
        // 计算RTP时间戳（单位：采样数）
        long rtpTimestamp = timestamp * CLOCK_RATE / 1000; // 转换为采样数

        // 构造RTP包
        byte[] packet = createRtpPacket(g711Data, rtpTimestamp);

        // 发送数据包
        sendPacket(packet);
    }

    private byte[] createRtpPacket(byte[] payload, long timestamp) {
        byte[] header = new byte[RTP_HEADER_SIZE];

        // RTP头字段设置
        header[0] = (byte) (0x80); // V=2, P=0, X=0, CC=0
        header[1] = (byte) (PAYLOAD_TYPE & 0x7F); // M=0, PT=0

        // 序列号（大端序）
        header[2] = (byte) (sequenceNumber >> 8);
        header[3] = (byte) (sequenceNumber);

        // 时间戳（大端序）
        header[4] = (byte) (timestamp >> 24);
        header[5] = (byte) (timestamp >> 16);
        header[6] = (byte) (timestamp >> 8);
        header[7] = (byte) (timestamp);

        // SSRC（大端序）
        header[8] = (byte) (ssrc >> 24);
        header[9] = (byte) (ssrc >> 16);
        header[10] = (byte) (ssrc >> 8);
        header[11] = (byte) (ssrc);

        // 合并头和负载
        byte[] packet = new byte[header.length + payload.length];
        System.arraycopy(header, 0, packet, 0, header.length);
        System.arraycopy(payload, 0, packet, header.length, payload.length);

        // 递增序列号（使用同步保证线程安全）
        synchronized (this) {
            sequenceNumber = (sequenceNumber + 1) & 0xFFFF;
        }

        return packet;
    }

    public void sendPacket(byte[] packet) {
        try {
            DatagramPacket datagramPacket = new DatagramPacket(
                    packet, packet.length, destAddress, destPort
            );
            socket.send(datagramPacket);
        } catch (Exception e) {
            throw new RuntimeException("RTP发送失败", e);
        }
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}

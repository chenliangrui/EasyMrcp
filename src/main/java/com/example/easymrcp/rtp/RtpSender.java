package com.example.easymrcp.rtp;

import java.net.DatagramPacket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class RtpSender {
    private static final int RTP_HEADER_SIZE = 12;
    private final DatagramSocket socket;
    private final InetAddress destAddress;
    private final int destPort;
    private int sequenceNumber = 0;
    private long timestamp = 0;

    public RtpSender(DatagramSocket socket, InetAddress destAddress, int destPort) {
        this.socket = socket;
        this.destAddress = destAddress;
        this.destPort = destPort;
    }

    public void send(byte[] payload, int payloadType) throws Exception {
        byte[] rtpPacket = new byte[RTP_HEADER_SIZE + payload.length];

        // 构造RTP头部
        rtpPacket[0] = (byte) (0x80); // V=2, P=0, X=0
        rtpPacket[1] = (byte) (payloadType & 0x7F); // M=0, PT=payloadType

        // 序列号（大端序）
        rtpPacket[2] = (byte) (sequenceNumber >> 8);
        rtpPacket[3] = (byte) (sequenceNumber);

        // 时间戳（大端序）
        System.arraycopy(longToBytes(timestamp), 4, rtpPacket, 4, 4);

        // 负载数据
        System.arraycopy(payload, 0, rtpPacket, RTP_HEADER_SIZE, payload.length);

        DatagramPacket packet = new DatagramPacket(
                rtpPacket,
                rtpPacket.length,
                destAddress,
                destPort
        );

        socket.send(packet);

        sequenceNumber++;  // 递增序列号
        timestamp += payload.length / 4; // 假设8kHz采样率，20ms帧
    }

    private byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return result;
    }
}
package com.cfsl.easymrcp.rtp;

public class RtpPacket {
    private int payloadType;
    private int sequenceNumber;
    private long timestamp;
    private byte[] payload;

    public static RtpPacket parseRtpHeader(byte[] data, int length) {
        RtpPacket packet = new RtpPacket();
        // 解析负载类型（第2字节低7位）
        packet.payloadType = data[1] & 0x7F;
        // 解析序列号（第2-3字节）
        packet.sequenceNumber = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        // 提取负载（跳过12字节头部）
        packet.payload = new byte[length - 12];
        System.arraycopy(data, 12, packet.payload, 0, packet.payload.length);
        return packet;
    }

    public byte[] getPayload() {
        return payload;
    }
}

package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import lombok.extern.slf4j.Slf4j;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class G711RtpSender {
    // RTP配置参数
    private int RTP_HEADER_SIZE = 12;
    private int PAYLOAD_TYPE = 8;  // G.711u的RTP载荷类型
    private long nextSendTime = 0;

    // RTP头部字段
//    private int sequenceNumber = new Random().nextInt(Short.MAX_VALUE);
    private int sequenceNumber = 0;
//    private int timestamp = new Random().nextInt(Integer.MAX_VALUE);
    private int timestamp = 0;
    private final int ssrc = new Random().nextInt(Integer.MAX_VALUE);

    // 网络参数
    private final DatagramSocket socket;
    private final InetAddress destAddress;
    private final int destPort;

    private boolean interrupt = false;

    public G711RtpSender(DatagramSocket socket, String destIp, int port) throws UnknownHostException {
        this.destAddress = InetAddress.getByName(destIp);
        this.destPort = port;
        this.socket = socket;
    }

    // 发送G.711u音频帧（每帧160字节，对应20ms音频）
    public void sendFrame(byte[] g711Data) throws Exception {
        int offset = 0;
        while (offset < g711Data.length && !interrupt) {
            int frameSize = Math.min(EMConstant.VOIP_SAMPLES_PER_FRAME, g711Data.length - offset);
            byte[] rtpPacket = buildRtpPacket(g711Data, offset, frameSize);
            DatagramPacket packet = new DatagramPacket(rtpPacket, rtpPacket.length, destAddress, destPort);

            socket.send(packet);

            long time = System.nanoTime();
            if (nextSendTime == 0) {
                nextSendTime = time;
            }
            long parkTime = time - nextSendTime;
            if (parkTime < -20 * 1000000) {
                LockSupport.parkNanos(20 * 1000000);
            } else  {
                LockSupport.parkNanos(-parkTime);
            }

            offset += frameSize;
            updateHeader(); // 更新序列号和时间戳
            nextSendTime += EMConstant.VOIP_FRAME_DURATION * 1000000;
        }
        interrupt = false;
    }

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

    private void updateHeader() {
        sequenceNumber = (sequenceNumber + 1) & 0xFFFF;
        timestamp += EMConstant.VOIP_SAMPLES_PER_FRAME; // 时间戳增量=8000*0.02=160
    }

    public void interrupt() {
        interrupt = true;
    }

    public void close() {
        socket.close();
    }
}
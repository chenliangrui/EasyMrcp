package com.example.easymrcp.rtp;

import lombok.Data;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

@Data
public class RtpReceiver {
    private String channelId;
    private static final int RTP_PORT = 5004; // RTP默认端口
    private static final int BUFFER_SIZE = 4096;

    public void run() {

                receive();

    }

    public void close() {
        System.out.println("RtpReceiver close");
    }

    private void receive() {
        try {
            DatagramSocket socket = new DatagramSocket(RTP_PORT);
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            new Thread(new Runnable() {
                @Override
                public void run() {

                    while (true) {
                        try {
                            socket.receive(packet); // 阻塞等待数据
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        byte[] rtpData = packet.getData();
                        int packetLength = packet.getLength();
                        System.out.println("RTP数据包长度：" + packetLength);
                        // 解析RTP头部和负载
//                RtpPacket parsedPacket = parseRtpPacket(rtpData, packetLength);
//                processPayload(parsedPacket.getPayload());
                    }
                }
            }).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

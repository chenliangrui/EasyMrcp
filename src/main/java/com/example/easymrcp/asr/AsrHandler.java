package com.example.easymrcp.asr;

import com.example.easymrcp.mrcp.AsrCallback;
import com.example.easymrcp.rtp.G711uDecoder;
import com.example.easymrcp.rtp.RtpConnection;
import com.example.easymrcp.rtp.RtpPacket;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import static com.example.easymrcp.rtp.RtpPacket.parseRtpHeader;

@Data
@Slf4j
public abstract class AsrHandler implements RtpConnection {
    @Setter
    @Getter
    private String channelId;
    @Setter
    private AsrCallback callback;

    private DatagramSocket socket;
    private int RTP_PORT; // RTP端口
    private static final int BUFFER_SIZE = 172;
    byte[] buffer = new byte[BUFFER_SIZE];
    protected Boolean stop = false;

    @Override
    public void create(String localIp, int localPort, String remoteIp, int remotePort) {
        this.RTP_PORT = localPort;
        this.create();
    }

    public abstract void create();

    public void receive() {
        try {
            socket = new DatagramSocket(RTP_PORT);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        try {
                            socket.receive(packet); // 阻塞等待数据
                        } catch (IOException e) {
                            if (e.getMessage().equals("socket closed")) {
                                log.info("rtp socket {} port closed", RTP_PORT);
                            } else {
                                log.error(e.getMessage(), e);
                            }
                            return;
                        }
                        byte[] rtpData = packet.getData();
                        int packetLength = packet.getLength();
//                        System.out.println("RTP数据包长度：" + packetLength);
                        // 解析RTP头部（前12字节）
                        RtpPacket parsedPacket = parseRtpHeader(rtpData, packetLength);
                        // 提取G.711u负载
                        byte[] g711Data = parsedPacket.getPayload();
                        // G.711u解码为PCM
                        byte[] pcmData = G711uDecoder.decodeG711U(g711Data);
                        receive(pcmData);
                    }
                }
            }).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    @Override
    public void close() {
        socket.close();
        asrClose();
    }

    public abstract void asrClose();

    public abstract void receive(byte[] pcmData);

}

package com.example.easymrcp.asr;

import com.example.easymrcp.mrcp.Callback;
import com.example.easymrcp.rtp.FunasrWsClient;
import com.example.easymrcp.rtp.RtpPacket;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.URI;

import static com.example.easymrcp.rtp.RtpPacket.parseRtpHeader;

@Data
@EqualsAndHashCode(callSuper = true)
public class FunAsrProcessor extends AsrHandler {
    private static final int RTP_PORT = 5004; // RTP默认端口
    private static final int BUFFER_SIZE = 172;
    byte[] buffer = new byte[BUFFER_SIZE];
    Boolean stop = false;

    private DatagramSocket socket;

    static String strChunkSize = "5,10,5";
    static int chunkInterval = 10;
    static int sendChunkSize = 1920;
    static String srvIp = "172.16.2.204";
    static String srvPort = "10096";
    FunasrWsClient funasrWsClient;

    Callback funasrCallback;

    private ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

    public void create(String ip, int port) {
        createFunAsrClient();
    }

    @Override
    public void close() {
        funasrWsClient.sendEof();
        socket.close();
        System.out.println("FunAsrProcessor close");
    }

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
                            throw new RuntimeException(e);
                        }
                        byte[] rtpData = packet.getData();
                        int packetLength = packet.getLength();
//                        System.out.println("RTP数据包长度：" + packetLength);
                        // 解析RTP头部（前12字节）
                        RtpPacket parsedPacket = parseRtpHeader(rtpData, packetLength);
                        // 提取G.711u负载
                        byte[] g711Data = parsedPacket.getPayload();
                        // G.711u解码为PCM
                        byte[] bytes = decodeG711U(g711Data);
                        funasrWsClient.recPcm(bytes);
                    }
                }
            }).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        stop = true;
    }

    // G.711U解码表（U-law转16位PCM）
    private static final short[] ULAW_TABLE = new short[256];

    static {
        for (int i = 0; i < 256; i++) {
            int ulaw = ~i; // U-law是补码存储
            int exponent = (ulaw >> 4) & 0x07;
            int mantissa = ulaw & 0x0F;
            short sample = (short) (((mantissa << 3) + 0x84) << exponent);
            if ((ulaw & 0x80) != 0) sample = (short) -sample;
            ULAW_TABLE[i] = sample;
        }
    }

    // 解码G.711U字节流为PCM
    public static byte[] decodeG711U(byte[] g711Data) {
        byte[] pcmData = new byte[g711Data.length * 2];
        for (int i = 0; i < g711Data.length; i++) {
            short sample = ULAW_TABLE[g711Data[i] & 0xFF];
            pcmData[i * 2] = (byte) (sample & 0xFF);
            pcmData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcmData;
    }

    public void createFunAsrClient() {
        try {
            int RATE = 8000;
            String[] chunkList = strChunkSize.split(",");
            int int_chunk_size = 60 * Integer.valueOf(chunkList[1].trim()) / chunkInterval;
            int CHUNK = Integer.valueOf(RATE / 1000 * int_chunk_size);
            int stride =
                    Integer.valueOf(
                            60 * Integer.valueOf(chunkList[1].trim()) / chunkInterval / 1000 * 8000 * 2);
            System.out.println("chunk_size:" + String.valueOf(int_chunk_size));
            System.out.println("CHUNK:" + CHUNK);
            System.out.println("stride:" + String.valueOf(stride));
            sendChunkSize = CHUNK * 2;

            String wsAddress = "ws://" + srvIp + ":" + srvPort;

            funasrCallback = new Callback() {
                @Override
                public void apply(String msg) {
                    getCallback().apply(msg);
                }
            };
            funasrWsClient = new FunasrWsClient(new URI(wsAddress), funasrCallback, stop);

            funasrWsClient.connect();

            System.out.println("wsAddress:" + wsAddress);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("e:" + e);
        }
    }
}

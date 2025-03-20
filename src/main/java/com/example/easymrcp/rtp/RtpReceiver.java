package com.example.easymrcp.rtp;

import lombok.Data;

import javax.sound.sampled.*;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static com.example.easymrcp.rtp.RtpPacket.parseRtpHeader;

@Data
public class RtpReceiver {
    private String channelId;
    private static final int RTP_PORT = 5004; // RTP默认端口
    private static final int BUFFER_SIZE = 4096000;
//    File outputFile = new File("D:\\code\\EasyMrcp\\src\\main\\java\\com\\example\\easymrcp\\testutils\\output.pcm");
    byte[] buffer = new byte[BUFFER_SIZE];
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    DatagramSocket socket;

    private FileOutputStream wavFile;
    String outputPath = "D:\\code\\EasyMrcp\\src\\main\\java\\com\\example\\easymrcp\\testutils\\output.wav";
    private ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

    public void run() {
        receive();
    }

    public void close() {
        // 保存录音到文件
        socket.close();
        System.out.println("RtpReceiver close");

        byte[] bytes = decodeG711U(audioBuffer.toByteArray());
        // 配置音频格式（G.711U的PCM参数）
        AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                8000.0f,   // 采样率8kHz
                16,        // 16位量化
                1,         // 单声道
                2,         // 每帧2字节（16位）
                8000.0f,   // 帧速率
                false      // 小端字节序
        );

        try {
            playPCM(bytes, audioFormat);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    private void receive() {
        try {
            // 创建文件输出流（首次写入WAV头）
            wavFile = new FileOutputStream(outputPath);
            socket = new DatagramSocket(RTP_PORT);
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
                        // 解析RTP头部（前12字节）
                        RtpPacket parsedPacket = parseRtpHeader(rtpData, packetLength);

                        // 提取G.711u负载
                        byte[] g711Data = parsedPacket.getPayload();

                        // G.711u解码为PCM
//                        short[] pcmData = G711uDecoder.decode(g711Data);

                        // 保存到文件
                        // 写入音频数据
                        try {
                            audioBuffer.write(g711Data);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 播放PCM音频流
    public static void playPCM(byte[] pcmData, AudioFormat format) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        InputStream input = new ByteArrayInputStream(pcmData);
        byte[] buffer = new byte[4096];
        try {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                line.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        line.drain();
        line.close();
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
}

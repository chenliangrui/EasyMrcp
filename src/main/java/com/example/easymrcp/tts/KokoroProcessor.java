package com.example.easymrcp.tts;

import com.example.easymrcp.rtp.RtpSender;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;

public class KokoroProcessor extends TtsHandler {
    private static final String API_URL = "http://172.16.2.207:8880/v1/audio/speech";
    private HttpClient httpClient;
    RtpSender rtpSender;
    private ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

    @Override
    public void transmit(String text) {
        String jsonPayload = String.format(
                "{\"model\":\"kokoro\",\"input\":\"%s\",\"voice\":\"zf_xiaoxiao\",\"response_format\":\"pcm\",\"stream\":true}",
                text);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        // 使用自定义的BodyHandler处理流式响应
        httpClient.sendAsync(request, new StreamBodyHandler())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        processAudioStream(response.body());
                    } else {
                        System.err.println("HTTP错误: " + response.statusCode());
                    }
                })
                .exceptionally(e -> {
                    System.err.println("请求异常: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                });
    }

    // 自定义响应体处理器
    private static class StreamBodyHandler implements HttpResponse.BodyHandler<InputStream> {
        @Override
        public HttpResponse.BodySubscriber<InputStream> apply(HttpResponse.ResponseInfo responseInfo) {
            return HttpResponse.BodySubscribers.ofInputStream();
        }
    }

    // 添加音频播放相关成员变量
    private AudioFormat audioFormat;
    private SourceDataLine sourceDataLine;

    // 在初始化方法中添加音频设备初始化
    private void initAudioPlayer() throws LineUnavailableException {
        audioFormat = new AudioFormat(24000, 16, 1, true, false); // 16kHz,16位,单声道
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
        sourceDataLine.open(audioFormat);
        sourceDataLine.start();
    }

    /**
     * 发送pcm数据
     *
     * @param inputStream
     */
    private void processAudioStream(InputStream inputStream) {
        try {
            initAudioPlayer();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        try (inputStream) {
            int bytesRead;
            byte[] buffer = new byte[1024];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // 确保线程安全的RTP发送
//                synchronized (this) {
                if (rtpSender != null) {
                    byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                    // 播放测试
//                        if (sourceDataLine != null && sourceDataLine.isOpen()) {
//                            sourceDataLine.write(buffer, 0, buffer.length);
//                        }
                    // 写入音频数据
                        try {
                            audioBuffer.write(chunk);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    rtpSender.send(chunk, 0); // PCMU负载类型
                }
//                }
            }
            // 播放测试
            // 配置音频格式（G.711U的PCM参数）
            AudioFormat audioFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    24000.0f,   // 采样率8kHz
                    16,        // 16位量化
                    1,         // 单声道
                    2,         // 每帧2字节（16位）
                    24000.0f,   // 帧速率
                    false      // 小端字节序
            );
            try {
                playPCM(audioBuffer.toByteArray(), audioFormat);
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("音频流处理异常: " + e.getMessage());
            e.printStackTrace();
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


    @Override
    public void create(String ip, int port) {
        //初始化rtp
        DatagramSocket udpSocket = null;
        try {
            udpSocket = new DatagramSocket();
            rtpSender = new RtpSender(udpSocket, InetAddress.getByName(ip), port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 创建kokoro的连接
        httpClient = HttpClient.newHttpClient();

    }

    @Override
    public void close() {

    }
}

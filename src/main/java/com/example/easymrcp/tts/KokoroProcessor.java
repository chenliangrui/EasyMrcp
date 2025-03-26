package com.example.easymrcp.tts;

import com.example.easymrcp.rtp.RtpSender;
import com.example.easymrcp.utils.G711UCodec;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;

public class KokoroProcessor extends TtsHandler {
    private static final String API_URL = "http://192.168.31.210:8880/v1/audio/speech";
    private HttpClient httpClient;
    RtpSender rtpSender;

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
            byte[] buffer = new byte[2048];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // 确保线程安全的RTP发送
//                synchronized (this) {
                    if (rtpSender != null) {
                        byte[] chunk = Arrays.copyOf(buffer, bytesRead  & ~1);
                        if (sourceDataLine != null && sourceDataLine.isOpen()) {
                            sourceDataLine.write(chunk, 0, chunk.length);
                        }
                        byte[] g711Chunk = G711UCodec.encode(chunk);
                        rtpSender.send(g711Chunk, 0); // PCMU负载类型
                    }
//                }
            }
        } catch (Exception e) {
            System.err.println("音频流处理异常: " + e.getMessage());
            e.printStackTrace();
        }
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

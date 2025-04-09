package com.example.easymrcp.tts.kokoro;

import com.example.easymrcp.tts.TTSConstant;
import com.example.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
public class KokoroProcessor extends TtsHandler {
    private static final String API_URL = "http://172.16.2.207:8880/v1/audio/speech";
    private HttpClient httpClient;

    @Override
    public void create() {
        // 创建kokoro的连接
        httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void speak(String text) {
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

    /**
     * 发送pcm数据
     *
     * @param inputStream
     */
    private void processAudioStream(InputStream inputStream) {
        try {
            try (inputStream) {
                byte[] pcmBuffer = new byte[409600];
                int bytesRead;
                while ((bytesRead = inputStream.read(pcmBuffer)) != -1) {
                    processor.putData(pcmBuffer, bytesRead);
                }
            }
            // tts语音合成结束，写入结束标志
            processor.putData(TTSConstant.TTS_END_FLAG, TTSConstant.TTS_END_FLAG.length);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void ttsClose() {
        log.info("Kokoro tts close");
    }

    // 自定义响应体处理器
    private static class StreamBodyHandler implements HttpResponse.BodyHandler<InputStream> {
        @Override
        public HttpResponse.BodySubscriber<InputStream> apply(HttpResponse.ResponseInfo responseInfo) {
            return HttpResponse.BodySubscribers.ofInputStream();
        }
    }
}

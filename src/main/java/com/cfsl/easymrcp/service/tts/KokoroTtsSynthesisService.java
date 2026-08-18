package com.cfsl.easymrcp.service.tts;

import com.alibaba.fastjson.JSON;
import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.tts.kokoro.KokoroConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直接调用 Kokoro HTTP 接口完成语音合成。
 */
@Service
public class KokoroTtsSynthesisService implements TtsSynthesisProvider {
    private final KokoroConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${audio.synthesis.timeout-seconds:60}")
    private long timeoutSeconds;

    public KokoroTtsSynthesisService(KokoroConfig config) {
        this.config = config;
    }

    @Override
    public String getEngineName() {
        return EMConstant.KOKORO;
    }

    @Override
    public byte[] synthesize(String text, String voice) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getModel());
        payload.put("input", text);
        payload.put("voice", voice == null || voice.isEmpty() ? config.getVoice() : voice);
        payload.put("response_format", "pcm");
        payload.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload)))
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Kokoro TTS 请求失败，HTTP 状态码: " + response.statusCode());
            }
            if (response.body().length == 0) {
                throw new IllegalStateException("Kokoro TTS 未返回音频");
            }
            // Kokoro 返回 24kHz PCM，按配置降采样后再封装为标准 8kHz WAV。
            return PcmWavUtils.normalizeTo8kWav(response.body(), config.getReSample());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kokoro TTS 合成被中断", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Kokoro TTS 合成失败", e);
        }
    }
}

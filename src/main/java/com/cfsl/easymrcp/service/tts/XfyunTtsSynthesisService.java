package com.cfsl.easymrcp.service.tts;

import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.tts.xfyun.XfyunTtsConfig;
import okhttp3.HttpUrl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 直接调用讯飞 WebSocket 接口完成语音合成。
 */
@Service
public class XfyunTtsSynthesisService implements TtsSynthesisProvider {
    private final XfyunTtsConfig config;

    @Value("${audio.synthesis.timeout-seconds:60}")
    private long timeoutSeconds;

    public XfyunTtsSynthesisService(XfyunTtsConfig config) {
        this.config = config;
    }

    @Override
    public String getEngineName() {
        return EMConstant.XFYUN;
    }

    @Override
    public byte[] synthesize(String text, String voice) {
        // 每次发布合成都创建独立连接，避免把请求状态保存到单例 Service 中。
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean successful = new AtomicBoolean();
        AtomicReference<Exception> failure = new AtomicReference<>();
        String actualVoice = voice == null || voice.isEmpty() ? config.getVoice() : voice;

        WebSocketClient client;
        try {
            String wsUrl = getAuthUrl(config.getHostUrl(), config.getAPIKey(), config.getAPISecret())
                    .replace("https://", "wss://");
            client = new WebSocketClient(new URI(wsUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    send(createRequest(text, actualVoice).toJSONString());
                }

                @Override
                public void onMessage(String message) {
                    JSONObject response = JSONObject.parseObject(message);
                    int code = response.getIntValue("code");
                    if (code != 0) {
                        failure.set(new IllegalStateException("讯飞 TTS 请求失败，错误码: " + code));
                        completed.countDown();
                        return;
                    }
                    JSONObject data = response.getJSONObject("data");
                    if (data == null) {
                        return;
                    }
                    String audio = data.getString("audio");
                    if (audio != null && !audio.isEmpty()) {
                        byte[] chunk = Base64.getDecoder().decode(audio);
                        pcm.write(chunk, 0, chunk.length);
                    }
                    if (data.getIntValue("status") == 2) {
                        successful.set(true);
                        completed.countDown();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (!successful.get() && failure.get() == null) {
                        failure.set(new IllegalStateException("讯飞 TTS 连接提前关闭: " + reason));
                    }
                    completed.countDown();
                }

                @Override
                public void onError(Exception e) {
                    failure.set(e);
                    completed.countDown();
                }
            };
        } catch (Exception e) {
            throw new IllegalStateException("讯飞 TTS 创建连接失败", e);
        }

        client.connect();
        try {
            if (!completed.await(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException("讯飞 TTS 合成超时");
            }
            if (failure.get() != null) {
                throw new IllegalStateException("讯飞 TTS 合成失败", failure.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("讯飞 TTS 合成被中断", e);
        } finally {
            client.close();
        }
        if (pcm.size() == 0) {
            throw new IllegalStateException("讯飞 TTS 未返回音频");
        }
        return PcmWavUtils.normalizeTo8kWav(pcm.toByteArray(), null);
    }

    private JSONObject createRequest(String text, String voice) {
        JSONObject common = new JSONObject();
        common.put("app_id", config.getAPPID());

        JSONObject business = new JSONObject();
        business.put("aue", "raw");
        business.put("tte", config.getTTE());
        business.put("auf", "audio/L16;rate=8000");
        business.put("ent", "intp65");
        business.put("vcn", voice);
        business.put("pitch", 50);
        business.put("speed", 50);

        JSONObject data = new JSONObject();
        data.put("status", 2);
        data.put("text", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));

        JSONObject request = new JSONObject();
        request.put("common", common);
        request.put("business", business);
        request.put("data", data);
        return request;
    }

    private String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(hostUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        String source = "host: " + url.getHost() + "\n"
                + "date: " + date + "\n"
                + "GET " + url.getPath() + " HTTP/1.1";

        Mac mac = Mac.getInstance("hmacsha256");
        mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));
        String authorization = String.format(
                "api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                apiKey, "hmac-sha256", "host date request-line", signature);
        HttpUrl httpUrl = Objects.requireNonNull(HttpUrl.parse("https://" + url.getHost() + url.getPath()))
                .newBuilder()
                .addQueryParameter("authorization", Base64.getEncoder().encodeToString(
                        authorization.getBytes(StandardCharsets.UTF_8)))
                .addQueryParameter("date", date)
                .addQueryParameter("host", url.getHost())
                .build();
        return httpUrl.toString();
    }
}

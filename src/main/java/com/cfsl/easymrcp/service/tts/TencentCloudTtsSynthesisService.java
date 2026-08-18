package com.cfsl.easymrcp.service.tts;

import com.google.gson.Gson;
import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.tts.tencentcloud.TxCloudTtsConfig;
import com.tencent.core.ws.Credential;
import com.tencent.core.ws.SpeechClient;
import com.tencent.ttsv2.SpeechSynthesizer;
import com.tencent.ttsv2.SpeechSynthesizerListener;
import com.tencent.ttsv2.SpeechSynthesizerRequest;
import com.tencent.ttsv2.SpeechSynthesizerResponse;
import com.tencent.ttsv2.TtsConstant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 直接调用腾讯云 Speech SDK 完成语音合成。
 */
@Service
public class TencentCloudTtsSynthesisService implements TtsSynthesisProvider {
    private static final int DEFAULT_VOICE_TYPE = 301036;

    private final TxCloudTtsConfig config;

    @Value("${audio.synthesis.timeout-seconds:60}")
    private long timeoutSeconds;

    public TencentCloudTtsSynthesisService(TxCloudTtsConfig config) {
        this.config = config;
    }

    @Override
    public String getEngineName() {
        return EMConstant.TENCENT_CLOUD;
    }

    @Override
    public byte[] synthesize(String text, String voice) {
        // 腾讯云通过 listener 返回分片，结束和失败回调共同决定本次 HTTP 合成结果。
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean successful = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();

        SpeechSynthesizerRequest request = new SpeechSynthesizerRequest();
        request.setText(text);
        request.setVoiceType(resolveVoiceType(voice));
        request.setVolume(0f);
        request.setSpeed(0f);
        request.setCodec("pcm");
        request.setSampleRate(8000);
        request.setEnableSubtitle(false);
        request.setSessionId(UUID.randomUUID().toString());
        request.set("SegmentRate", 0);

        SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {
            @Override
            public void onSynthesisStart(SpeechSynthesizerResponse response) {
            }

            @Override
            public void onSynthesisEnd(SpeechSynthesizerResponse response) {
                successful.set(true);
                completed.countDown();
            }

            @Override
            public void onAudioResult(ByteBuffer buffer) {
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                pcm.write(data, 0, data.length);
            }

            @Override
            public void onTextResult(SpeechSynthesizerResponse response) {
            }

            @Override
            public void onSynthesisFail(SpeechSynthesizerResponse response) {
                failure.set(new Gson().toJson(response));
                completed.countDown();
            }
        };

        SpeechClient client = new SpeechClient(TtsConstant.DEFAULT_TTS_REQ_URL);
        Credential credential = new Credential(config.getAppId(), config.getSecretId(), config.getSecretKey());
        SpeechSynthesizer synthesizer = null;
        try {
            synthesizer = new SpeechSynthesizer(client, credential, request, listener);
            synthesizer.start();
            synthesizer.stop();
            if (!completed.await(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException("腾讯云 TTS 合成超时");
            }
            if (!successful.get()) {
                throw new IllegalStateException("腾讯云 TTS 合成失败: " + failure.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("腾讯云 TTS 合成被中断", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("腾讯云 TTS 合成失败", e);
        } finally {
            if (synthesizer != null) {
                synthesizer.close();
            }
        }
        if (pcm.size() == 0) {
            throw new IllegalStateException("腾讯云 TTS 未返回音频");
        }
        return PcmWavUtils.normalizeTo8kWav(pcm.toByteArray(), null);
    }

    private int resolveVoiceType(String voice) {
        String actualVoice = voice == null || voice.isEmpty() ? config.getVoice() : voice;
        if (actualVoice == null || actualVoice.isEmpty()) {
            return DEFAULT_VOICE_TYPE;
        }
        try {
            return Integer.parseInt(actualVoice);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("腾讯云音色必须是 VoiceType 数字: " + actualVoice, e);
        }
    }
}

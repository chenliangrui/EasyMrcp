package com.cfsl.easymrcp.service.tts;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;
import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.tts.aliyun.AliyunTtsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 直接调用 DashScope SDK 完成阿里云语音合成。
 */
@Service
public class AliyunTtsSynthesisService implements TtsSynthesisProvider {
    private final AliyunTtsConfig config;

    @Value("${audio.synthesis.timeout-seconds:60}")
    private long timeoutSeconds;

    public AliyunTtsSynthesisService(AliyunTtsConfig config) {
        this.config = config;
    }

    @Override
    public String getEngineName() {
        return EMConstant.ALIYUN;
    }

    @Override
    public byte[] synthesize(String text, String voice) {
        if (config.getBaseWebsocketApiUrl() != null && !config.getBaseWebsocketApiUrl().isEmpty()) {
            Constants.baseWebsocketApiUrl = config.getBaseWebsocketApiUrl();
        }
        String actualVoice = voice == null || voice.isEmpty() ? config.getVoice() : voice;
        String actualModel = config.getMode();
        if (actualVoice != null && !actualVoice.startsWith("cosyvoice-")) {
            actualModel = "cosyvoice-v3-flash";
        }
        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .apiKey(config.getAPIKey())
                .model(actualModel)
                .voice(actualVoice)
                .format(SpeechSynthesisAudioFormat.PCM_8000HZ_MONO_16BIT)
                .build();

        // DashScope 通过异步回调返回音频，当前 HTTP 请求需要等待明确的完成或失败信号。
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, new ResultCallback<SpeechSynthesisResult>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                ByteBuffer frame = result.getAudioFrame();
                if (frame != null) {
                    byte[] data = new byte[frame.remaining()];
                    frame.get(data);
                    pcm.write(data, 0, data.length);
                }
            }

            @Override
            public void onComplete() {
                completed.countDown();
            }

            @Override
            public void onError(Exception e) {
                failure.set(e);
                completed.countDown();
            }
        });
        try {
            synthesizer.call(text);
            if (!completed.await(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException("阿里云 TTS 合成超时");
            }
            if (failure.get() != null) {
                throw new IllegalStateException("阿里云 TTS 合成失败", failure.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("阿里云 TTS 合成被中断", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("阿里云 TTS 合成失败", e);
        } finally {
            if (synthesizer.getDuplexApi() != null) {
                synthesizer.getDuplexApi().close(1000, "bye");
            }
        }
        if (pcm.size() == 0) {
            throw new IllegalStateException("阿里云 TTS 未返回音频");
        }
        return PcmWavUtils.normalizeTo8kWav(pcm.toByteArray(), null,
                config.getSkipBytesInTheFirstPacket(), config.getSkipBytesInTheEndPacket());
    }
}

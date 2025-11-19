package com.cfsl.easymrcp.tts.aliyun;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.cfsl.easymrcp.tts.TTSConstant;
import com.cfsl.easymrcp.tts.TtsEngine;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class AliyunCosyVoiceEngine extends TtsEngine {
    // 模型
    private static String model;
    public String APIKey;

    private SpeechSynthesizer synthesizer;
    private final CountDownLatch latch = new CountDownLatch(1);

    public AliyunCosyVoiceEngine(AliyunTtsConfig aliyunTtsConfig) {
        APIKey = aliyunTtsConfig.getAPIKey();
        model = aliyunTtsConfig.getMode();
    }

    @Override
    public void create() {
        // 实现回调接口ResultCallback
        ResultCallback<SpeechSynthesisResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                if (result.getAudioFrame() != null) {
                    ByteBuffer audioFrame = result.getAudioFrame();
                    byte[] audioBytes = new byte[audioFrame.remaining()];
                    audioFrame.get(audioBytes);
                    putAudioData(audioBytes, audioBytes.length);
                }
            }

            @Override
            public void onComplete() {
                putAudioData(TTSConstant.TTS_END_FLAG.retainedDuplicate());
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                log.error("阿里云语音合成出现异常", e);
                latch.countDown();
            }
        };

        // 请求参数
        SpeechSynthesisParam param =
                SpeechSynthesisParam.builder()
                        // 若没有将API Key配置到环境变量中，需将下面这行代码注释放开，并将your-api-key替换为自己的API Key
                        .apiKey(APIKey)
                        .model(model) // 模型
                        .voice(voice) // 音色
                        .format(SpeechSynthesisAudioFormat.PCM_8000HZ_MONO_16BIT)
                        .build();
        // 第二个参数“callback”传入回调即启用异步模式
        synthesizer = new SpeechSynthesizer(param, callback);
    }

    @Override
    public void speak(String text) {
        // 非阻塞调用，立即返回null（实际结果通过回调接口异步传递），在回调接口的onEvent方法中实时获取二进制音频
        try {
            synthesizer.call(text);
            // 等待合成完成
            latch.await();
            // 等待播放线程全部播放完
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            // 任务结束后关闭websocket连接
            synthesizer.getDuplexApi().close(1000, "bye");
        }
    }

    @Override
    public void ttsClose() {
        // 任务结束后关闭websocket连接
        synthesizer.getDuplexApi().close(1000, "bye");
    }
}

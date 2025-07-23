package com.cfsl.easymrcp.tts.tencentcloud;

import com.cfsl.easymrcp.tts.TTSConstant;
import com.cfsl.easymrcp.tts.TtsHandler;
import com.google.gson.Gson;
import com.tencent.core.ws.Credential;
import com.tencent.core.ws.SpeechClient;
import com.tencent.ttsv2.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.UUID;

@Slf4j
public class TxCloudTtsClient {
    TtsHandler ttsHandler;
    SpeechClient proxy;
    TxCloudTtsConfig config;
    Credential credential;

    public TxCloudTtsClient(TxCloudTtsConfig config, TtsHandler ttsHandler) {
        this.config = config;
        this.ttsHandler = ttsHandler;
    }

    public void create() {
        proxy = new SpeechClient(TtsConstant.DEFAULT_TTS_REQ_URL);
        credential = new Credential(config.getAppId(), config.getSecretId(), config.getSecretKey());
    }

    public void speak(String text) {
        SpeechSynthesizerRequest request = new SpeechSynthesizerRequest();
        request.setText(text);
        request.setVoiceType(301036);
        request.setVolume(0f);
        request.setSpeed(0f);
        request.setCodec("pcm");
        request.setSampleRate(8000);
        request.setEnableSubtitle(true);
        request.setEmotionCategory("happy");
        request.setEmotionIntensity(100);
        request.setSessionId(UUID.randomUUID().toString());//sessionId，需要保持全局唯一（推荐使用 uuid），遇到问题需要提供该值方便服务端排查
        request.set("SegmentRate", 0); //sdk暂未支持参数，可通过该方法设置
        log.debug("session_id:{}", request.getSessionId());
        SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {//tips：回调方法中应该避免进行耗时操作，如果有耗时操作建议进行异步处理否则会影响websocket请求处理

            @Override
            public void onSynthesisStart(SpeechSynthesizerResponse response) {
                log.info("{} session_id:{},{}", "onSynthesisStart", response.getSessionId(), new Gson().toJson(response));
            }

            @Override
            public void onSynthesisEnd(SpeechSynthesizerResponse response) {
                log.info("{} session_id:{},{}", "onSynthesisEnd", response.getSessionId(), new Gson().toJson(response));
                // tts语音合成结束，写入结束标志
                try {
                    // 直接使用TtsHandler的putAudioData方法
                    ttsHandler.putAudioData(TTSConstant.TTS_END_FLAG, TTSConstant.TTS_END_FLAG.length);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }

            @Override
            public void onAudioResult(ByteBuffer buffer) {
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                try {
                    // 直接使用TtsHandler的putAudioData方法
                    ttsHandler.putAudioData(data, data.length);
                } catch (Exception e) {
                    log.error("处理音频数据失败", e);
                }
            }

            @Override
            public void onTextResult(SpeechSynthesizerResponse response) {
                log.info("{} session_id:{},{}", "onTextResult", response.getSessionId(), new Gson().toJson(response));
            }

            @Override
            public void onSynthesisFail(SpeechSynthesizerResponse response) {
                log.info("{} session_id:{},{}", "onSynthesisFail", response.getSessionId(), new Gson().toJson(response));
            }
        };
        //synthesizer不可重复使用，每次合成需要重新生成新对象
        SpeechSynthesizer synthesizer = null;
        try {
            synthesizer = new SpeechSynthesizer(proxy, credential, request, listener);
            long currentTimeMillis = System.currentTimeMillis();
            synthesizer.start();
            log.info("synthesizer start latency : " + (System.currentTimeMillis() - currentTimeMillis) + " ms");
            currentTimeMillis = System.currentTimeMillis();
            synthesizer.stop();
            log.info("synthesizer stop latency : " + (System.currentTimeMillis() - currentTimeMillis) + " ms");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            if (synthesizer != null) {
                synthesizer.close(); //关闭连接
            }
        }
    }
}

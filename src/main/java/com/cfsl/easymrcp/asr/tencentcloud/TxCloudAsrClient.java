package com.cfsl.easymrcp.asr.tencentcloud;

import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.google.gson.Gson;
import com.tencent.asrv2.*;
import com.tencent.core.ws.Credential;
import com.tencent.core.ws.SpeechClient;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class TxCloudAsrClient {

    SpeechClient proxy = new SpeechClient(AsrConstant.DEFAULT_RT_REQ_URL);
    TxCloudAsrConfig txCloudConfig;
    SpeechRecognizer speechRecognizer;
    AsrCallback txCloudCallback;
    long currentTimeMillis;

    public TxCloudAsrClient(TxCloudAsrConfig txCloudConfig, AsrCallback txCloudCallback) {
        this.txCloudConfig = txCloudConfig;
        this.txCloudCallback = txCloudCallback;
    }

    public void create() {
        Credential credential = new Credential(txCloudConfig.getAppId(), txCloudConfig.getSecretId(), txCloudConfig.getSecretKey());
        SpeechRecognizerRequest request = SpeechRecognizerRequest.init();
        request.setEngineModelType("8k_zh");
        request.setVoiceFormat(1);
        request.setVoiceId(UUID.randomUUID().toString());//voice_id为请求标识，需要保持全局唯一（推荐使用 uuid），遇到问题需要提供该值方便服务端排查
        // request.set("hotword_list", "腾讯云|10,语音识别|5,ASR|11"); //sdk暂未支持参数，可通过该方法设置
        log.debug("voice_id:{}", request.getVoiceId());
        SpeechRecognizerListener listener = new SpeechRecognizerListener() {//tips：回调方法中应该避免进行耗时操作，如果有耗时操作建议进行异步处理否则会影响websocket请求处理
            @Override
            public void onRecognitionStart(SpeechRecognizerResponse response) {//首包回调
                log.info("{} voice_id:{},{}", "onRecognitionStart", response.getVoiceId(), new Gson().toJson(response));
            }

            @Override
            public void onSentenceBegin(SpeechRecognizerResponse response) {//一段话开始识别 slice_type=0
                log.info("{} voice_id:{},{}", "onSentenceBegin", response.getVoiceId(), new Gson().toJson(response));
            }

            @Override
            public void onRecognitionResultChange(SpeechRecognizerResponse response) {//一段话识别中，slice_type=1,voice_text_str 为非稳态结果(该段识别结果还可能变化)
                log.info(" {} voice_id:{},{}", "onRecognitionResultChange", response.getVoiceId(), new Gson().toJson(response));
            }

            @Override
            public void onSentenceEnd(SpeechRecognizerResponse response) {//一段话识别结束，slice_type=2,voice_text_str 为稳态结果(该段识别结果不再变化)
                log.info("{} voice_id:{},{}", "onSentenceEnd", response.getVoiceId(), new Gson().toJson(response));
                txCloudCallback.apply(response.getResult().getVoiceTextStr());
            }

            @Override
            public void onRecognitionComplete(SpeechRecognizerResponse response) {//识别完成回调 即final=1
                log.info("{} voice_id:{},{}", "onRecognitionComplete", response.getVoiceId(), new Gson().toJson(response));
            }

            @Override
            public void onFail(SpeechRecognizerResponse response) {//失败回调
                log.info("{} voice_id:{},{}", "onFail", response.getVoiceId(), new Gson().toJson(response));
            }

            @Override
            public void onMessage(SpeechRecognizerResponse response) {//所有消息都会回调该方法
                log.info("{} voice_id:{},{}", "onMessage", response.getVoiceId(), new Gson().toJson(response));
            }
        };
        try {
            speechRecognizer = new SpeechRecognizer(proxy, credential, request, listener);
            speechRecognizer.start();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void receive(byte[] pcmData) {
        speechRecognizer.write(pcmData);
    }

    public void sendEof() {
        try {
            speechRecognizer.stop();
            log.info("tencent asr cost: {}ms", System.currentTimeMillis() - currentTimeMillis);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void asrClose() {
        speechRecognizer.close();
    }
}

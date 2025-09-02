package com.cfsl.easymrcp.tts.tencentcloud;

import com.cfsl.easymrcp.tts.TtsHandler;
import com.cfsl.easymrcp.tts.TtsProcessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TxCloudTtsProcessor extends TtsProcessor {
    TxCloudTtsConfig config;
    TxCloudTtsClient txCloudTtsClient;

    public TxCloudTtsProcessor(TxCloudTtsConfig config) {
        this.config = config;
    }

    @Override
    public void create() {
        // 直接传递this，以便TxCloudTtsClient可以调用putAudioData方法
        txCloudTtsClient = new TxCloudTtsClient(config, ttsHandler);
        txCloudTtsClient.create();
    }

    @Override
    public void speak(String text) {
        txCloudTtsClient.speak(text);
    }

    @Override
    public void ttsClose() {
        log.info("txCloudTtsClient close");
    }
}

package com.cfsl.easymrcp.tts.tencentcloud;

import com.cfsl.easymrcp.tts.RealTimeAudioProcessor;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TxCloudTtsProcessor extends TtsHandler {
    TxCloudTtsConfig config;
    TxCloudTtsClient txCloudTtsClient;

    public TxCloudTtsProcessor(TxCloudTtsConfig config) {
        this.config = config;
    }

    @Override
    public void create() {
        txCloudTtsClient = new TxCloudTtsClient(config, processor);
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

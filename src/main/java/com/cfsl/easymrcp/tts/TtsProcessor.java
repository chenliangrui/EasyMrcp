package com.cfsl.easymrcp.tts;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
public class TtsProcessor {
    private ExecutorService executorService;

    @Setter
    protected TtsHandler ttsHandler;

    @Getter
    Map<String, TtsEngine> ttsEngines = new HashMap<>();

    public TtsProcessor(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void setTtsEngine(String ttsEngineId, TtsEngine ttsEngine) {
        ttsEngines.put(ttsEngineId, ttsEngine);
    }

    public void removeTtsEngine(String ttsEngineId) {
        ttsEngines.remove(ttsEngineId);
    }

    public void createAndSpeak(TtsEngine ttsEngine, String text) {
        executorService.execute(() -> {
            ttsEngine.create();
            ttsEngine.speak(text);
        });
    }

    public void playPreLoad(String ttsEngineId) {
        TtsEngine ttsEngine = ttsEngines.get(ttsEngineId);
        if (ttsEngine != null) {
            executorService.execute(ttsEngine::playPreLoadData);
        } else {
            log.error("预加载引擎为null: {}", ttsEngineId);
        }
    }

    public void ttsClose() {
        for (TtsEngine ttsEngine : ttsEngines.values()) {
            ttsEngine.ttsClose();
        }
    }
}

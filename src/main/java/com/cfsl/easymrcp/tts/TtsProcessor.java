package com.cfsl.easymrcp.tts;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class TtsProcessor {
    private ExecutorService executorService;

    @Setter
    protected TtsHandler ttsHandler;

    @Getter
    List<TtsEngine> ttsEngines = new ArrayList<>();

    public TtsProcessor(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void setTtsEngine(TtsEngine ttsEngine) {
        ttsEngines.add(ttsEngine);
    }

    public void createAndSpeak(TtsEngine ttsEngine, String text) {
        executorService.execute(() -> {
            ttsEngine.create();
            ttsEngine.speak(text);
            ttsEngines.clear();
        });
    }

    public void ttsClose() {
        for (TtsEngine ttsEngine : ttsEngines) {
            ttsEngine.ttsClose();
        }
    }
}

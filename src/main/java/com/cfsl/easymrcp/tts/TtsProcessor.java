package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.AudioCacheService;
import com.cfsl.easymrcp.common.CachedAudio;
import com.cfsl.easymrcp.utils.SpringUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
public class TtsProcessor {
    private ExecutorService executorService;
    private final AudioCacheService audioCacheService;

    @Setter
    protected TtsHandler ttsHandler;

    @Getter
    Map<String, TtsEngine> ttsEngines = new HashMap<>();

    public TtsProcessor(ExecutorService executorService) {
        this(executorService, null);
    }

    public TtsProcessor(ExecutorService executorService, AudioCacheService audioCacheService) {
        this.executorService = executorService;
        this.audioCacheService = audioCacheService;
    }

    public void setTtsEngine(String ttsEngineId, TtsEngine ttsEngine) {
        ttsEngines.put(ttsEngineId, ttsEngine);
    }

    public void removeTtsEngine(String ttsEngineId) {
        ttsEngines.remove(ttsEngineId);
    }

    public void createAndSpeak(TtsEngine ttsEngine, TtsRequest request) {
        executorService.execute(() -> {
            if (request.hasCache()) {
                try {
                    AudioCacheService cacheService = audioCacheService == null
                            ? SpringUtils.getBean(AudioCacheService.class) : audioCacheService;
                    CachedAudio audio = cacheService.getOrDownload(request.getCache());
                    ttsEngine.setCacheHit(true);
                    ttsEngine.setCharCount(audio.getCharCount());
                    ttsEngine.playPcm(audio.getPcm());
                    return;
                } catch (Exception e) {
                    log.warn("录音缓存不可用，回退实时TTS, objectName={}, reason={}",
                            request.getCache(), e.getMessage());
                }
            }
            ttsEngine.setCacheHit(false);
            ttsEngine.setCharCount(request.getText().length());
            ttsEngine.create();
            ttsEngine.speak(request.getText());
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

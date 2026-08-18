package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.AudioCacheService;
import com.cfsl.easymrcp.common.CachedAudio;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsProcessorAudioCacheTest {
    @Test
    void cacheHitSkipsVendorCreateAndSpeak() {
        FakeEngine engine = new FakeEngine();
        TtsProcessor processor = new TtsProcessor(new DirectExecutor(), new StubCache(false));

        processor.createAndSpeak(engine, new TtsRequest("回退文本", "cached.wav"));

        assertTrue(engine.isCacheHit());
        assertEquals(5, engine.getCharCount());
        assertEquals(0, engine.createCount);
        assertEquals(0, engine.speakCount);
        assertEquals(1, engine.playCount);
    }

    @Test
    void cacheFailureUsesOriginalRealtimeTtsPath() {
        FakeEngine engine = new FakeEngine();
        TtsProcessor processor = new TtsProcessor(new DirectExecutor(), new StubCache(true));

        processor.createAndSpeak(engine, new TtsRequest("回退文本", "missing.wav"));

        assertFalse(engine.isCacheHit());
        assertEquals(4, engine.getCharCount());
        assertEquals(1, engine.createCount);
        assertEquals(1, engine.speakCount);
        assertEquals("回退文本", engine.lastText);
    }

    private static class StubCache extends AudioCacheService {
        private final boolean fail;

        StubCache(boolean fail) {
            this.fail = fail;
        }

        @Override
        public CachedAudio getOrDownload(String objectName) {
            if (fail) {
                throw new IllegalStateException("download failed");
            }
            return new CachedAudio(new byte[320], 5);
        }
    }

    private static class FakeEngine extends TtsEngine {
        int createCount;
        int speakCount;
        int playCount;
        String lastText;

        @Override
        public void create() {
            createCount++;
        }

        @Override
        public void speak(String text) {
            speakCount++;
            lastText = text;
        }

        @Override
        public void ttsClose() {
        }

        @Override
        public void playPcm(byte[] pcm) {
            playCount++;
        }
    }

    private static class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}

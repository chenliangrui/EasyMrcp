package com.cfsl.easymrcp.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioCacheServiceConcurrencyTest {
    @TempDir
    Path cacheDir;

    @Test
    void sameObjectNameRunsOnlyOneDownload() throws Exception {
        byte[] wav = wav(new byte[] {1, 2, 3, 4});
        StubAudioCacheService service = new StubAudioCacheService(wav);
        ReflectionTestUtils.setField(service, "cacheDir", cacheDir.toString());
        ReflectionTestUtils.setField(service, "allowedPrefix", "tts-cache/agent-speak/");
        String objectName = "tts-cache/agent-speak/20260817/1001_v1_c2_s" + wav.length + "_"
                + AudioCacheService.sha256(wav) + ".wav";

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<CachedAudio>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> service.getOrDownload(objectName));
            }
            List<Future<CachedAudio>> results = executor.invokeAll(tasks);
            for (Future<CachedAudio> result : results) {
                assertEquals(4, result.get().getPcm().length);
            }
            assertEquals(1, service.downloadCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static class StubAudioCacheService extends AudioCacheService {
        private final byte[] wav;
        private final AtomicInteger downloadCount = new AtomicInteger();

        StubAudioCacheService(byte[] wav) {
            this.wav = wav;
        }

        @Override
        protected void download(AudioObjectName metadata, Path target) {
            downloadCount.incrementAndGet();
            try {
                Thread.sleep(100);
                Files.createDirectories(target.getParent());
                Files.write(target, wav);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static byte[] wav(byte[] pcm) {
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(8000);
        header.putInt(16000);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(pcm.length);
        byte[] result = new byte[44 + pcm.length];
        System.arraycopy(header.array(), 0, result, 0, 44);
        System.arraycopy(pcm, 0, result, 44, pcm.length);
        return result;
    }
}

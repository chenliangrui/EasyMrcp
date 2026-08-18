package com.cfsl.easymrcp.common;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
public class AudioCacheService {
    private final ConcurrentMap<String, CompletableFuture<CachedAudio>> downloads = new ConcurrentHashMap<>();

    @Value("${audio.oss.enabled:false}")
    private boolean enabled;
    @Value("${audio.oss.endpoint:}")
    private String endpoint;
    @Value("${audio.oss.bucket:}")
    private String bucket;
    @Value("${audio.oss.access-key-id:}")
    private String accessKeyId;
    @Value("${audio.oss.access-key-secret:}")
    private String accessKeySecret;
    @Value("${audio.oss.allowed-prefix:tts-cache/agent-speak/}")
    private String allowedPrefix;
    @Value("${audio.cache.dir:${java.io.tmpdir}/easymrcp-audio-cache}")
    private String cacheDir;
    @Value("${audio.cache.retain-days:1}")
    private int retainDays;

    private volatile OSS ossClient;

    public CachedAudio getOrDownload(String objectName) {
        AudioObjectName metadata = AudioObjectName.parse(objectName, allowedPrefix);
        Path target = cachePath(metadata);
        if (isValid(target, metadata)) {
            return readAudio(target, metadata);
        }
        CompletableFuture<CachedAudio> current = new CompletableFuture<>();
        CompletableFuture<CachedAudio> running = downloads.putIfAbsent(objectName, current);
        if (running != null) {
            try {
                return running.join();
            } catch (CompletionException e) {
                throw new IllegalStateException("下载录音缓存失败", e.getCause());
            }
        }
        try {
            if (!isValid(target, metadata)) {
                download(metadata, target);
            }
            CachedAudio audio = readAudio(target, metadata);
            current.complete(audio);
            return audio;
        } catch (RuntimeException e) {
            current.completeExceptionally(e);
            throw e;
        } finally {
            downloads.remove(objectName, current);
        }
    }

    protected void download(AudioObjectName metadata, Path target) {
        if (!enabled) {
            throw new IllegalStateException("录音 OSS 缓存未启用");
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(part);
            OSSObject object = client().getObject(bucket, metadata.getObjectName());
            try (InputStream input = object.getObjectContent()) {
                Files.copy(input, part, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!isValid(part, metadata)) {
                throw new IllegalStateException("录音文件完整性校验失败");
            }
            try {
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("下载录音缓存失败", e);
        }
    }

    private CachedAudio readAudio(Path path, AudioObjectName metadata) {
        try {
            byte[] wav = Files.readAllBytes(path);
            return new CachedAudio(extractPcm(wav), metadata.getCharCount());
        } catch (IOException e) {
            throw new IllegalStateException("读取录音缓存失败", e);
        }
    }

    private boolean isValid(Path path, AudioObjectName metadata) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) != metadata.getSize()) {
                return false;
            }
            byte[] wav = Files.readAllBytes(path);
            return metadata.getSha256().equals(sha256(wav)) && isPcmWav(wav);
        } catch (Exception e) {
            return false;
        }
    }

    private Path cachePath(AudioObjectName metadata) {
        return Paths.get(cacheDir).toAbsolutePath().normalize().resolve(sha256(
                metadata.getObjectName().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".wav");
    }

    private OSS client() {
        OSS current = ossClient;
        if (current == null) {
            synchronized (this) {
                current = ossClient;
                if (current == null) {
                    current = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                    ossClient = current;
                }
            }
        }
        return current;
    }

    public static boolean isPcmWav(byte[] wav) {
        if (wav == null || wav.length < 44 || !ascii(wav, 0, "RIFF") || !ascii(wav, 8, "WAVE")) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getShort(20) == 1 && buffer.getShort(22) == 1
                && buffer.getInt(24) == 8000 && buffer.getShort(34) == 16;
    }

    public static byte[] extractPcm(byte[] wav) {
        if (!isPcmWav(wav)) {
            throw new IllegalArgumentException("录音不是 8kHz/16bit/mono PCM WAV");
        }
        int offset = 12;
        while (offset + 8 <= wav.length) {
            String id = new String(wav, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int length = ByteBuffer.wrap(wav, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int dataOffset = offset + 8;
            if ("data".equals(id) && length >= 0 && dataOffset + length <= wav.length) {
                byte[] pcm = new byte[length];
                System.arraycopy(wav, dataOffset, pcm, 0, length);
                return pcm;
            }
            offset = dataOffset + length + (length & 1);
        }
        throw new IllegalArgumentException("WAV 缺少 data 块");
    }

    static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static boolean ascii(byte[] data, int offset, String expected) {
        if (offset + expected.length() > data.length) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (data[offset + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Scheduled(cron = "${audio.cache.cleanup-cron:0 20 3 * * ?}")
    public void cleanupExpired() {
        Path root = Paths.get(cacheDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return;
        }
        Instant threshold = Instant.now().minus(Duration.ofDays(Math.max(1, retainDays)));
        try (java.util.stream.Stream<Path> paths = Files.list(root)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toInstant().isBefore(threshold)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException e) {
                    log.warn("清理录音缓存失败: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("扫描录音缓存目录失败: {}", root, e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}

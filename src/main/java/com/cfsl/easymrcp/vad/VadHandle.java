package com.cfsl.easymrcp.vad;

import ai.onnxruntime.OrtException;
import com.cfsl.easymrcp.utils.SipUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 新版VAD处理器，使用vadNew包中的SlieroVadDetector实现
 * 支持能量阈值过滤，提高VAD检测准确性
 * 
 * @author VvvvvGH
 */
@Slf4j
public class VadHandle {
    private static final String MODEL_PATH = "silero_vad.onnx";
    private static final int SAMPLE_RATE = 8000;
    private static final float START_THRESHOLD = 0.4f;
    private static final float END_THRESHOLD = 0.8f;
    // Speech-Complete-Timeout默认使用300毫秒
    private static int MIN_SILENCE_DURATION_MS = 300;
    private static final int SPEECH_PAD_MS = 500;
    // 能量阈值，用于过滤背景噪音
    private static final float ENERGY_THRESHOLD = 0.05f;
    // 动态能量阈值倍数（阈值 = 平均能量 × 倍数）
    private static final float ENERGY_THRESHOLD_MULTIPLIER = 2.0f;

    private SlieroVadDetector vadDetector;
    @Getter
    private Boolean isSpeaking = false;

    public VadHandle() {
        initVad();
    }

    /**
     * 使用指定的静音超时时长初始化VAD
     *
     * @param speechCompleteTimeoutMs Speech-Complete-Timeout参数值（毫秒）
     */
    public VadHandle(Long speechCompleteTimeoutMs) {
        if (speechCompleteTimeoutMs != null && speechCompleteTimeoutMs > 0) {
            MIN_SILENCE_DURATION_MS = speechCompleteTimeoutMs.intValue();
            log.info("Using custom Speech-Complete-Timeout value for VAD: {} ms", MIN_SILENCE_DURATION_MS);
        }
        initVad();
    }

    /**
     * 设置Speech-Complete-Timeout参数
     * 注意：新版VAD不支持动态修改此参数，需要重新初始化
     *
     * @param speechCompleteTimeoutMs 静音超时时长（毫秒）
     */
    public void setSpeechCompleteTimeout(Long speechCompleteTimeoutMs) {
        if (speechCompleteTimeoutMs != null && speechCompleteTimeoutMs > 0
                && speechCompleteTimeoutMs.intValue() != MIN_SILENCE_DURATION_MS) {
            log.warn("VadHandleNew does not support dynamic Speech-Complete-Timeout modification. " +
                    "Please reinitialize with new timeout value.");
        }
    }

    private void initVad() {
        try {
            String modePath = resolveModelPath();
            vadDetector = new SlieroVadDetector(
                    modePath,
                    START_THRESHOLD,
                    END_THRESHOLD,
                    SAMPLE_RATE,
                    MIN_SILENCE_DURATION_MS,
                    SPEECH_PAD_MS,
                    ENERGY_THRESHOLD,
                    ENERGY_THRESHOLD_MULTIPLIER
            );
            log.info("VadHandleNew initialized with MIN_SILENCE_DURATION_MS: {} ms, ENERGY_THRESHOLD: {}, MULTIPLIER: {}", 
                    MIN_SILENCE_DURATION_MS, ENERGY_THRESHOLD, ENERGY_THRESHOLD_MULTIPLIER);
        } catch (OrtException e) {
            log.error("Error initializing the VAD detector: {}", e.getMessage(), e);
        }
    }

    /**
     * 解析模型文件路径
     */
    private String resolveModelPath() {
        String path = System.getProperty("user.dir");
        File file = new File(path + File.separator + "src" + File.separator + "main" + 
                File.separator + "resources" + File.separator + MODEL_PATH);
        
        if (!file.exists()) {
            // 尝试在当前目录查找
            return path + File.separator + MODEL_PATH;
        } else {
            return file.getAbsolutePath();
        }
    }

    /**
     * 接收PCM音频数据并进行VAD检测
     * 
     * @param pcmData PCM音频数据（16位，单声道）
     */
    public void receivePcm(byte[] pcmData) {
        try {
            Map<String, Double> detectResult = vadDetector.apply(pcmData, true);

            if (detectResult != null && !detectResult.isEmpty()) {
                if (detectResult.containsKey("start")) {
                    isSpeaking = true;
                    log.debug("VAD detected speech start at {}s, probability: {}, energy: {}", 
                            detectResult.get("start"), 
                            detectResult.get("probability"),
                            detectResult.get("energy"));
                } else if (detectResult.containsKey("end")) {
                    isSpeaking = false;
                    log.debug("VAD detected speech end at {}s, probability: {}, energy: {}", 
                            detectResult.get("end"),
                            detectResult.get("probability"),
                            detectResult.get("energy"));
                }
            }
        } catch (Exception e) {
            log.error("Error applying VAD detector: {}", e.getMessage(), e);
        }
    }

    /**
     * 延时释放VAD资源，防止出现onnxruntime原生空指针错误从而导致jvm崩溃
     */
    public void release() {
        SipUtils.wheelTimer.newTimeout(timeout -> {
            SipUtils.executeTask(() -> {
                try {
                    if (vadDetector != null) {
                        log.debug("VadHandleNew released");
                        vadDetector.close();
                    }
                } catch (OrtException e) {
                    log.error("Error closing VAD detector: {}", e.getMessage(), e);
                }
            });
        }, 2000, TimeUnit.MILLISECONDS);
    }
}

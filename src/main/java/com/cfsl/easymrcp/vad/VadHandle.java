package com.cfsl.easymrcp.vad;

import ai.onnxruntime.OrtException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Map;

@Slf4j
public class VadHandle {
    private static final String MODEL_PATH = "silero_vad.onnx";
    private static final int SAMPLE_RATE = 8000;
    private static final float START_THRESHOLD = 0.6f;
    private static final float END_THRESHOLD = 0.8f;
    // Speech-Complete-Timeout默认使用600毫秒
    private static int MIN_SILENCE_DURATION_MS = 300;
    private static final int SPEECH_PAD_MS = 500;
    private static final int WINDOW_SIZE_SAMPLES = 2048;

    SlieroVadDetector vadDetector;
    @Getter
    private Boolean isSpeaking = false;

    public VadHandle() {
        initVad();
    }
    
    /**
     * 使用指定的静音超时时长初始化VAD
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
     * 设置Speech-Complete-Timeout参数并重新初始化VAD
     * @param speechCompleteTimeoutMs 静音超时时长（毫秒）
     */
    public void setSpeechCompleteTimeout(Long speechCompleteTimeoutMs) {
        if (speechCompleteTimeoutMs != null && speechCompleteTimeoutMs > 0 
                && speechCompleteTimeoutMs.intValue() != MIN_SILENCE_DURATION_MS) {
            MIN_SILENCE_DURATION_MS = speechCompleteTimeoutMs.intValue();
            log.info("Updating Speech-Complete-Timeout value for VAD: {} ms", MIN_SILENCE_DURATION_MS);
            
            // 重新初始化VAD检测器
            try {
                if (vadDetector != null) {
                    vadDetector.close();
                }
                initVad();
            } catch (Exception e) {
                log.error("Error reinitializing VAD with new timeout: {}", e.getMessage());
            }
        }
    }
    
    private void initVad() {
        // Initialize the Voice Activity Detector
        try {
            String modePath = null;
            String path = System.getProperty("user.dir");
            File file = new File(path + "\\src\\main\\resources\\" + MODEL_PATH);
            if (!file.exists()) {
                String os = System.getProperty("os.name");
                if (os != null && os.toLowerCase().startsWith("windows")) {
                    modePath = path + "\\" + MODEL_PATH;
                } else if (os != null && os.toLowerCase().startsWith("linux")) {
                    modePath = path + "/" + MODEL_PATH;
                }
            } else {
                modePath = path + "\\src\\main\\resources\\" + MODEL_PATH;
            }
            vadDetector = new SlieroVadDetector(modePath, START_THRESHOLD, END_THRESHOLD, SAMPLE_RATE, MIN_SILENCE_DURATION_MS, SPEECH_PAD_MS);
            log.info("VAD initialized with MIN_SILENCE_DURATION_MS: {} ms", MIN_SILENCE_DURATION_MS);
        } catch (OrtException e) {
            log.error("Error initializing the VAD detector: {}", e.getMessage());
        }
    }

    public void receivePcm(byte[] pcmData) {
        // Apply the Voice Activity Detector to the data and get the result
        Map<String, Double> detectResult = null;
        try {
            detectResult = vadDetector.apply(pcmData, true);
        } catch (Exception e) {
            log.error("Error applying VAD detector: {}", e.getMessage());
        }

        if (detectResult != null && !detectResult.isEmpty()) {
            if (detectResult.containsKey("start")
            ) {
                isSpeaking = true;
            } else if (detectResult.containsKey("end")) {
                isSpeaking = false;
            }
            log.trace("vad status: {}", detectResult);
        }
    }

    public void release() {
        try {
            vadDetector.close();
        } catch (OrtException e) {
            log.error("Error closing VAD detector: {}", e.getMessage());
        }
    }
}

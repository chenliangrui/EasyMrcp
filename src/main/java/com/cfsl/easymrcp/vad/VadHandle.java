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
    private static final float END_THRESHOLD = 0.45f;
    private static final int MIN_SILENCE_DURATION_MS = 600;
    private static final int SPEECH_PAD_MS = 500;
    private static final int WINDOW_SIZE_SAMPLES = 2048;

    SlieroVadDetector vadDetector;
    @Getter
    private Boolean isSpeaking = false;
    // 为解决vad会重复判断的问题，这里记录上一次vad时间，讲话间隔小于1秒的不视为讲话
    private Double lastVad = 0.0;

    public VadHandle() {
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
        } catch (OrtException e) {
            System.err.println("Error initializing the VAD detector: " + e.getMessage());
        }
    }

    public void receivePcm(byte[] pcmData) {
        // Apply the Voice Activity Detector to the data and get the result
        Map<String, Double> detectResult = null;
        try {
            detectResult = vadDetector.apply(pcmData, true);
        } catch (Exception e) {
            System.err.println("Error applying VAD detector: " + e.getMessage());
        }

        if (!detectResult.isEmpty()) {
            if (detectResult.containsKey("start") && detectResult.get("start") - lastVad > 1) {
                isSpeaking = true;
            } else if (detectResult.containsKey("end")) {
                isSpeaking = false;
                lastVad = detectResult.get("end");
            }
            log.trace("vad status: {}", detectResult);
        }
    }

    public void release() {

    }
}

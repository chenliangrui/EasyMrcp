package com.example.easymrcp.vad;

import ai.onnxruntime.OrtException;
import com.example.easymrcp.asr.ASRConstant;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class VadHandle {
    private static final String MODEL_PATH = "D:\\code\\EasyMrcp\\src\\main\\java\\com\\example\\easymrcp\\vad\\silero_vad.onnx";
    private static final int SAMPLE_RATE = 8000;
    private static final float START_THRESHOLD = 0.6f;
    private static final float END_THRESHOLD = 0.45f;
    private static final int MIN_SILENCE_DURATION_MS = 600;
    private static final int SPEECH_PAD_MS = 500;
    private static final int WINDOW_SIZE_SAMPLES = 2048;

    SlieroVadDetector vadDetector;
    @Getter
    private Boolean isSpeaking = false;

    public VadHandle() {
        // Initialize the Voice Activity Detector
        try {
            vadDetector = new SlieroVadDetector(MODEL_PATH, START_THRESHOLD, END_THRESHOLD, SAMPLE_RATE, MIN_SILENCE_DURATION_MS, SPEECH_PAD_MS);
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
            if (detectResult.containsKey("start")) {
                isSpeaking = true;
            } else if (detectResult.containsKey("end")) {
                isSpeaking = false;
            }
            log.info(detectResult.toString());
        }
    }

    public void release() {

    }
}

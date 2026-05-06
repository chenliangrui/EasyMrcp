package com.cfsl.easymrcp.vad;

import ai.onnxruntime.OrtException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Silero VAD Detector with Energy Threshold
 * Real-time voice activity detection with energy-based filtering
 *
 * @author VvvvvGH
 */
public class SlieroVadDetector {
    // ONNX model for speech processing
    private final SlieroVadOnnxModel model;
    // Speech start threshold
    private final float startThreshold;
    // Speech end threshold
    private final float endThreshold;
    // Sampling rate
    private final int samplingRate;
    // Minimum silence samples to determine speech end
    private final float minSilenceSamples;
    // Speech padding samples for calculating speech boundaries
    private final float speechPadSamples;
    // Minimum energy threshold floor used for background-noise filtering
    private final float minEnergyThreshold;
    // Energy threshold multiplier for dynamic calculation
    private final float energyThresholdMultiplier;
    // Exponential moving average factor for noise floor updates
    private final float noiseFloorAlpha = 0.05f;
    // Estimated noise floor energy
    private float noiseFloorEnergy;
    // Current energy threshold used to filter background noise
    private float energyThreshold;
    // Triggered state (whether speech is being detected)
    private boolean triggered;
    // Temporary speech end sample position
    private int tempEnd;
    // Current sample position
    private int currentSample;
    // Reusable buffer for audio conversion
    private float[] audioBuffer;
    // Reusable result map
    private final Map<String, Double> resultMap;

    public SlieroVadDetector(String modelPath,
                             float startThreshold,
                             float endThreshold,
                             int samplingRate,
                             int minSilenceDurationMs,
                             int speechPadMs) throws OrtException {
        this(modelPath, startThreshold, endThreshold, samplingRate, minSilenceDurationMs, speechPadMs, 0.01f, 1.4f);
    }

    public SlieroVadDetector(String modelPath,
                             float startThreshold,
                             float endThreshold,
                             int samplingRate,
                             int minSilenceDurationMs,
                             int speechPadMs,
                             float energyThreshold) throws OrtException {
        this(modelPath, startThreshold, endThreshold, samplingRate, minSilenceDurationMs, speechPadMs, energyThreshold, 1.4f);
    }

    public SlieroVadDetector(String modelPath,
                             float startThreshold,
                             float endThreshold,
                             int samplingRate,
                             int minSilenceDurationMs,
                             int speechPadMs,
                             float energyThreshold,
                             float energyThresholdMultiplier) throws OrtException {
        // Validate sampling rate
        if (samplingRate != 8000 && samplingRate != 16000) {
            throw new IllegalArgumentException("Does not support sampling rates other than [8000, 16000]");
        }

        // Initialize parameters
        this.model = new SlieroVadOnnxModel(modelPath);
        this.startThreshold = startThreshold;
        this.endThreshold = endThreshold;
        this.samplingRate = samplingRate;
        this.minSilenceSamples = samplingRate * minSilenceDurationMs / 1000f;
        this.speechPadSamples = samplingRate * speechPadMs / 1000f;
        this.minEnergyThreshold = energyThreshold;
        this.energyThresholdMultiplier = energyThresholdMultiplier;
        this.resultMap = new HashMap<>(3);
        // Reset state
        reset();
    }

    /**
     * Reset detector state
     */
    public void reset() {
        model.resetStates();
        triggered = false;
        tempEnd = 0;
        currentSample = 0;
        audioBuffer = null;
        noiseFloorEnergy = minEnergyThreshold;
        energyThreshold = minEnergyThreshold;
    }

    /**
     * Calculate RMS (Root Mean Square) energy of audio data
     *
     * @param audioData Audio samples
     * @param length Actual length to calculate
     * @return RMS energy value
     */
    private float calculateRMSEnergy(float[] audioData, int length) {
        float sum = 0.0f;
        for (int i = 0; i < length; i++) {
            float sample = audioData[i];
            sum += sample * sample;
        }
        return (float) Math.sqrt(sum / length);
    }

    private void updateNoiseFloorIfNeeded(float rmsEnergy) {
        if (!triggered) {
            noiseFloorEnergy = noiseFloorAlpha * rmsEnergy + (1 - noiseFloorAlpha) * noiseFloorEnergy;
        }
        energyThreshold = Math.max(minEnergyThreshold, noiseFloorEnergy * energyThresholdMultiplier);
    }

    /**
     * Process audio data and detect speech events
     *
     * @param data Audio data as byte array
     * @param returnSeconds Whether to return timestamps in seconds
     * @return Speech event (start or end) or empty map if no event
     */
    public Map<String, Double> apply(byte[] data, boolean returnSeconds) {

        int numSamples = data.length / 2;

        // Reuse buffer if possible
        if (audioBuffer == null || audioBuffer.length != numSamples) {
            audioBuffer = new float[numSamples];
        }

        // Convert byte array to float array
        for (int i = 0; i < numSamples; i++) {
            audioBuffer[i] = ((data[i * 2] & 0xff) | (data[i * 2 + 1] << 8)) / 32767.0f;
        }

        // Get window size from audio data length
        int windowSizeSamples = numSamples;
        // Update current sample position
        currentSample += windowSizeSamples;

        // Calculate RMS energy
        float rmsEnergy = calculateRMSEnergy(audioBuffer, numSamples);

        // Get speech probability from model
        float speechProb = 0;
        try {
            speechProb = model.call(new float[][]{audioBuffer}, samplingRate)[0];
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        updateNoiseFloorIfNeeded(rmsEnergy);

        // Reset temporary end if speech probability exceeds threshold
        if (speechProb >= startThreshold && tempEnd != 0) {
            tempEnd = 0;
        }

        // Detect speech start - requires BOTH VAD and energy threshold
        if (speechProb >= startThreshold && !triggered) {
            // Check energy threshold to filter out background noise
            if (rmsEnergy >= energyThreshold) {
                triggered = true;
                int speechStart = (int) (currentSample - speechPadSamples);
                speechStart = Math.max(speechStart, 0);

                // Reuse result map
                resultMap.clear();

                // Return in seconds or samples based on returnSeconds parameter
                if (returnSeconds) {
                    double speechStartSeconds = speechStart / (double) samplingRate;
                    double roundedSpeechStart = Math.round(speechStartSeconds * 10.0) / 10.0;
                    resultMap.put("start", roundedSpeechStart);
                } else {
                    resultMap.put("start", (double) speechStart);
                }
                resultMap.put("probability", (double) speechProb);
                resultMap.put("energy", (double) rmsEnergy);
                return resultMap;
            } else {
                return Collections.emptyMap();
            }
        }

        // Detect speech end
        if (speechProb < endThreshold && triggered) {
            // Initialize or update temporary end position
            if (tempEnd == 0) {
                tempEnd = currentSample;
            }
            // Wait for minimum silence duration before confirming speech end
            if (currentSample - tempEnd < minSilenceSamples) {
                return Collections.emptyMap();
            } else {
                // Calculate speech end time and reset state
                int speechEnd = (int) (tempEnd + speechPadSamples);
                tempEnd = 0;
                triggered = false;

                // Reuse result map
                resultMap.clear();

                if (returnSeconds) {
                    double speechEndSeconds = speechEnd / (double) samplingRate;
                    double roundedSpeechEnd = BigDecimal.valueOf(speechEndSeconds).setScale(1, RoundingMode.HALF_UP).doubleValue();
                    resultMap.put("end", roundedSpeechEnd);
                } else {
                    resultMap.put("end", (double) speechEnd);
                }
                resultMap.put("probability", (double) speechProb);
                resultMap.put("energy", (double) rmsEnergy);
                return resultMap;
            }
        }

        // No speech event detected
        return Collections.emptyMap();
    }

    public void close() throws OrtException {
        reset();
        model.close();
    }
}

package com.cfsl.easymrcp.vad;

import ai.onnxruntime.OrtException;

import javax.sound.sampled.*;

/**
 * Realtime Silero VAD Java Example
 * Voice Activity Detection using microphone input
 * 
 * @author VvvvvGH
 */
public class App {

    // ONNX model path - using the model file from the project
    private static final String MODEL_PATH = "D:\\code\\EasyMrcp\\src\\main\\resources\\silero_vad.onnx";
    // Sampling rate
    private static final int SAMPLE_RATE = 16000;
    // Speech threshold (consistent with Python default)
    private static final float THRESHOLD = 0.6f;
    // Negative threshold (used to determine speech end)
    private static final float NEG_THRESHOLD = 0.9f; // threshold - 0.15
    // Minimum silence duration (milliseconds)
    private static final int MIN_SILENCE_DURATION_MS = 100;
    // Speech padding (milliseconds)
    private static final int SPEECH_PAD_MS = 30;
    // Window size (samples) - 512 samples for 16kHz
    private static final int WINDOW_SIZE_SAMPLES = 2048;
    // Energy threshold for filtering background noise (RMS value)
    private static final float ENERGY_THRESHOLD = 0.05f;

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Realtime Silero VAD Java ONNX Example");
        System.out.println("=".repeat(60));
        
        // Initialize realtime VAD detector
        SlieroVadDetector vadDetector;
        try {
            System.out.println("Loading ONNX model: " + MODEL_PATH);
            vadDetector = new SlieroVadDetector(
                MODEL_PATH,
                THRESHOLD,
                NEG_THRESHOLD,
                SAMPLE_RATE,
                MIN_SILENCE_DURATION_MS,
                SPEECH_PAD_MS,
                ENERGY_THRESHOLD
            );
            System.out.println("VAD detector initialized successfully!");
        } catch (OrtException e) {
            System.err.println("Failed to initialize VAD detector: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Set audio format
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        // Get the target data line and open it with the specified format
        TargetDataLine targetDataLine;
        try {
            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(format);
            targetDataLine.start();
            System.out.println("\n" + "=".repeat(60));
            System.out.println("Microphone opened successfully!");
            System.out.println("Listening for speech... (Press Ctrl+C to stop)");
            System.out.println("=".repeat(60) + "\n");
        } catch (LineUnavailableException e) {
            System.err.println("Error opening target data line: " + e.getMessage());
            try {
                vadDetector.close();
            } catch (OrtException ex) {
                System.err.println("Error closing VAD detector: " + ex.getMessage());
            }
            return;
        }

        // Main loop to continuously read data and apply Voice Activity Detection
        byte[] buffer = new byte[WINDOW_SIZE_SAMPLES * 2]; // 2 bytes per sample (16-bit)
        
        while (targetDataLine.isOpen()) {
            int numBytesRead = targetDataLine.read(buffer, 0, buffer.length);
            if (numBytesRead <= 0) {
                System.err.println("Error reading data from microphone.");
                continue;
            }

            // Apply the Voice Activity Detector to the data and get the result
            try {
                vadDetector.apply(buffer, true);
            } catch (Exception e) {
                System.err.println("Error applying VAD detector: " + e.getMessage());
                continue;
            }
        }

        // Close resources
        targetDataLine.close();
        try {
            vadDetector.close();
        } catch (OrtException e) {
            System.err.println("Error closing VAD detector: " + e.getMessage());
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("VAD stopped.");
        System.out.println("=".repeat(60));
    }
}

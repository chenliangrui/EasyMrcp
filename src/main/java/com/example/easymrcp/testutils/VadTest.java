package com.example.easymrcp.testutils;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class VadTest {
    // 音频参数
    private static final int SAMPLE_RATE = 8000;
    private static final int FRAME_SIZE = 160; // 20ms * 8kHz
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit PCM

    // VAD参数
    private static final double ENERGY_THRESHOLD_FACTOR = 1.5; // 能量阈值倍数
    private static final int ZCR_THRESHOLD = 25; // 过零率阈值
    private static final int NOISE_FRAMES = 10; // 初始噪声估计帧数
    // 初始化VAD参数
    static double noiseEnergy = 0;
    static int frameCount = 0;

    public static void main(String[] args) throws Exception {
        // 初始化音频输入流
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();


        byte[] buffer = new byte[FRAME_SIZE * BYTES_PER_SAMPLE];

        while (true) {
            // 读取一帧音频数据（160采样点）
            int bytesRead = line.read(buffer, 0, buffer.length);
            if (bytesRead != buffer.length) break;

            // 转换为short数组（16-bit PCM）
            short[] samples = new short[FRAME_SIZE];
            for (int i = 0; i < FRAME_SIZE; i++) {
                samples[i] = (short) ((buffer[2*i+1] << 8) | (buffer[2*i] & 0xFF));
            }

            // 计算短时能量（STE）
            double energy = calculateSTE(samples);

            // 计算过零率（ZCR）
            int zcr = calculateZCR(samples);

            // 初始噪声估计阶段
            if (frameCount < NOISE_FRAMES) {
                noiseEnergy += energy;
                frameCount++;
                if (frameCount == NOISE_FRAMES) {
                    noiseEnergy /= NOISE_FRAMES;
                    System.out.println("噪声能量估计: " + noiseEnergy);
                }
                continue;
            }

            // VAD判决
            boolean isSpeech = energy > noiseEnergy * ENERGY_THRESHOLD_FACTOR &&
                    zcr < ZCR_THRESHOLD;

            // 动态更新噪声能量（仅静音时更新）
            if (!isSpeech) {
                noiseEnergy = 0.9 * noiseEnergy + 0.1 * energy;
            }

            System.out.printf("Frame: STE=%.2f, ZCR=%d, Speech=%b%n", energy, zcr, isSpeech);
        }
        line.close();
    }

    public static boolean vad(byte[] buffer) {
        // 转换为short数组（16-bit PCM）
        short[] samples = new short[FRAME_SIZE];
        for (int i = 0; i < FRAME_SIZE; i++) {
            samples[i] = (short) ((buffer[2*i+1] << 8) | (buffer[2*i] & 0xFF));
        }

        // 计算短时能量（STE）
        double energy = calculateSTE(samples);

        // 计算过零率（ZCR）
        int zcr = calculateZCR(samples);

        // 初始噪声估计阶段
        if (frameCount < NOISE_FRAMES) {
            noiseEnergy += energy;
            frameCount++;
            if (frameCount == NOISE_FRAMES) {
                noiseEnergy /= NOISE_FRAMES;
                System.out.println("噪声能量估计: " + noiseEnergy);
            }
            return false;
        }

        // VAD判决
        boolean isSpeech = energy > noiseEnergy * ENERGY_THRESHOLD_FACTOR &&
                zcr < ZCR_THRESHOLD;

        // 动态更新噪声能量（仅静音时更新）
        if (!isSpeech) {
            noiseEnergy = 0.9 * noiseEnergy + 0.1 * energy;
        }

        System.out.printf("Frame: STE=%.2f, ZCR=%d, Speech=%b%n", energy, zcr, isSpeech);
        return isSpeech;
    }

    // 计算短时能量（STE）
    private static double calculateSTE(short[] samples) {
        double sum = 0;
        for (short s : samples) {
            sum += s * s;
        }
        return sum / samples.length;
    }

    // 计算过零率（ZCR）
    private static int calculateZCR(short[] samples) {
        int crossings = 0;
        for (int i = 1; i < samples.length; i++) {
            if ((samples[i-1] >= 0 && samples[i] < 0) ||
                    (samples[i-1] < 0 && samples[i] >= 0)) {
                crossings++;
            }
        }
        return crossings;
    }
}

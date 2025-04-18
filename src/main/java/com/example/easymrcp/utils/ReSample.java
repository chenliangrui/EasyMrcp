package com.example.easymrcp.utils;

//8k采样率转16k采样率
public class ReSample {
    // 重采样核心算法（线性插值+简单滤波）
    public static byte[] resampleFrame(byte[] inputFrame) {
        int inputSamples = inputFrame.length / 2; // 16-bit samples
        int outputSamples = inputSamples * 2;
        byte[] outputFrame = new byte[outputSamples * 2];

        short prev = 0;
        for (int i = 0; i < inputSamples; i++) {
            // 读取当前样本（小端序）
            short current = (short) ((inputFrame[i*2] & 0xFF) | (inputFrame[i*2+1] << 8));

            // 抗混叠滤波（一阶低通）
            float alpha = 0.15f;
            short filtered = (short) (prev + alpha * (current - prev));

            // 线性插值
            short middle = (short) ((prev + filtered) / 2);

            // 写入插值样本
            writeSample(outputFrame, i*4, prev);
            writeSample(outputFrame, i*4+2, middle);

            prev = filtered;
        }
        return outputFrame;
    }

    private static void writeSample(byte[] buffer, int pos, short sample) {
        buffer[pos] = (byte) (sample & 0xFF);
        buffer[pos+1] = (byte) ((sample >> 8) & 0xFF);
    }
}

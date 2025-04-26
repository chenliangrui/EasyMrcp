package com.cfsl.easymrcp.tts;

public class G711UEncoder {

    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;
    private static final int[] MU_LAW_TABLE = new int[256];

    static {
        // 初始化μ-law量化表（预计算优化）
        for (int i = 0; i < 256; i++) {
            int input = i ^ 0xff;
            int mantissa = (input & 0xf) << 4;
            int segment = (input & 0x70) >> 4;
            int value = mantissa + 8 + (segment << (segment + 3));
            MU_LAW_TABLE[i] = (input & 0x80) != 0 ? (BIAS + value) : (-BIAS - value);
        }
    }

    /**
     * PCM转G.711u编码（μ-law）
     * @param pcmData 16位小端序PCM字节数组（采样率8kHz）
     * @return G.711u编码后的字节数组
     */
    public static byte[] encode(byte[] pcmData) {
        byte[] g711Data = new byte[pcmData.length / 2];
        for (int i = 0, j = 0; i < pcmData.length; i += 2, j++) {
            // 将两个字节组合为16位有符号整数（小端序）
            short sample = (short) ((pcmData[i + 1] & 0xff) << 8 | (pcmData[i] & 0xff));
            g711Data[j] = encodeSample(sample);
        }
        return g711Data;
    }

    private static byte encodeSample(short pcm) {
        // 1. 限幅处理（确保输入在-32635~32635范围内）
        int sign = (pcm & 0x8000) != 0 ? 0x80 : 0;
        int magnitude = Math.min(Math.abs(pcm), CLIP);

        // 2. 添加偏移量（μ-law特性）
        magnitude += BIAS;

        // 3. 对数压缩（通过查表实现高效编码）
        int exponent = 7;
        for (int mask = 0x4000; (magnitude & mask) == 0 && exponent > 0; mask >>= 1, exponent--);

        int mantissa = (magnitude >> (exponent + 3)) & 0x0f;
        byte encoded = (byte) (sign | (exponent << 4) | mantissa);

        // 4. 取反操作（μ-law规范要求）
        return (byte) (encoded ^ 0xff);
    }
}
package com.example.easymrcp.utils;

public class G711UCodec {
    private static final short BIAS = 0x84;
    private static final short CLIP = 32635;

    /**
     * 将16位PCM数据转换为G.711 μ-law编码格式
     * @param pcmData 小端格式的16位PCM数据
     * @return μ-law编码字节数组
     */
    public static byte[] encode(byte[] pcmData) {
        if (pcmData.length % 2 != 0) {
            throw new IllegalArgumentException("PCM数据长度必须为偶数");
        }

        int sampleCount = pcmData.length / 2;
        byte[] ulawData = new byte[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            // 小端字节序转换为short
            short sample = (short) (((pcmData[2*i + 1] & 0xFF) << 8) |
                    (pcmData[2*i] & 0xFF));
            ulawData[i] = linearToULaw(sample);
        }

        return ulawData;
    }

    private static byte linearToULaw(short pcm) {
        // 1. 获取符号位（0x80如果为负）
        int sign = (pcm & 0x8000) >> 8;
        int magnitude;

        // 2. 处理负数并获取绝对值
        if (sign != 0) {
            magnitude = -pcm;
            sign = 0x80;  // μ-law符号位
        } else {
            magnitude = pcm;
        }

        // 3. 添加偏置（改善小信号量化性能）
        magnitude += 0x84;  // 132的偏置值

        // 4. 限幅处理（防止溢出）
        if (magnitude > 0x7FFF) magnitude = 0x7FFF;

        // 5. 查找最高有效位位置
        int exponent = 7;
        int mask = 0x4000;  // 从第14位开始检测

        while ((magnitude & mask) == 0 && exponent > 0) {
            mask >>= 1;
            exponent--;
        }

        // 6. 计算尾数（取4位有效值）
        int mantissa = (magnitude >> (exponent + 3)) & 0x0F;

        // 7. 组合编码并取反
        byte ulaw = (byte) (sign | (exponent << 4) | mantissa);
        return (byte) ~ulaw;
    }
}

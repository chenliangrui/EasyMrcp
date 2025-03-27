package com.example.easymrcp.utils;

public class G711UCodec {
    // μ-law压缩表（动态生成）
    private static final byte[] ULAW_TABLE = new byte[8192];

    static {
        // 初始化μ-law编码表（覆盖14位输入范围）
        for (int i = 0; i < ULAW_TABLE.length; i++) {
            int pcm = (i & 0x2000) != 0 ? (i - 0x4000) : i; // 转换为有符号14位
            ULAW_TABLE[i] = encodeSample((short)pcm);
        }
    }

    /**
     * 将16位PCM音频转换为G.711u字节流
     * @param pcmData 16位有符号PCM数据（建议先做14位裁剪）
     * @return 压缩后的G.711u字节数组
     */
    public static byte[] encode(short[] pcmData) {
        byte[] encoded = new byte[pcmData.length];
        for (int i = 0; i < pcmData.length; i++) {
            // 添加范围校验和压缩处理
            int sample = pcmData[i];
            // 将16位样本转换为14位绝对值（右移2位）
            int absSample = Math.abs(sample) >> 2;
            // 限制最大有效值为0x1FFF（8191）
            if (absSample > 0x1FFF) {
                absSample = 0x1FFF;
            }
            // 组合符号位和幅度值
            int index = absSample | ((sample < 0) ? 0x2000 : 0);
            encoded[i] = ULAW_TABLE[index & 0x1FFF]; // 确保索引在0-8191范围内
        }
        return encoded;
    }

    public static short[] bytesToShorts(byte[] byteArray, boolean isLittleEndian) {
        int shortLen = byteArray.length / 2;
        short[] shorts = new short[shortLen];

        for (int i = 0; i < shortLen; i++) {
            int pos = i * 2;
            int b1 = byteArray[pos] & 0xFF;
            int b2 = byteArray[pos + 1] & 0xFF;
            shorts[i] = isLittleEndian ?
                    (short) (b1 | (b2 << 8)) :
                    (short) ((b1 << 8) | b2);
        }
        return shorts;
    }

    // 核心编码算法（参考ITU-T标准）
    private static byte encodeSample(short sample) {
        int sign = (sample & 0x8000) >> 8; // 符号位
        int abs = Math.abs(sample);

        // 添加μ-law压缩偏移量
        if (abs < 0x100) abs += 0xFF;
        else abs += 0xFF << (abs >> 8);

        // 计算分段和量化值
        int exponent = 7 - ((32 - Integer.numberOfLeadingZeros(abs)) - 8);
        exponent = Math.max(0, Math.min(exponent, 7));
        int mantissa = (abs >> (exponent + 3)) & 0x0F;

        return (byte) (~(sign | (exponent << 4) | mantissa));
    }
}

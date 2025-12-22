package com.cfsl.easymrcp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * G.711 μ-law (PCMU) 编解码工具。
 * 参考 ITU-T G.711 与 Sun/CCITT 公开实现，采用查表解码与指数查表编码，兼顾正确性与性能。
 */
public final class G711UUtil {

    private static final int BIAS = 0x84;           // 132
    private static final int CLIP = 32635;

    // μ-law 解码查找表（标准值，性能最佳）
    private static final short[] ULAW_TO_LINEAR = new short[] {
        -32124, -31100, -30076, -29052, -28028, -27004, -25980, -24956,
        -23932, -22908, -21884, -20860, -19836, -18812, -17788, -16764,
        -15996, -15484, -14972, -14460, -13948, -13436, -12924, -12412,
        -11900, -11388, -10876, -10364, -9852, -9340, -8828, -8316,
        -7932, -7676, -7420, -7164, -6908, -6652, -6396, -6140,
        -5884, -5628, -5372, -5116, -4860, -4604, -4348, -4092,
        -3900, -3772, -3644, -3516, -3388, -3260, -3132, -3004,
        -2876, -2748, -2620, -2492, -2364, -2236, -2108, -1980,
        -1884, -1820, -1756, -1692, -1628, -1564, -1500, -1436,
        -1372, -1308, -1244, -1180, -1116, -1052, -988, -924,
        -876, -844, -812, -780, -748, -716, -684, -652,
        -620, -588, -556, -524, -492, -460, -428, -396,
        -372, -356, -340, -324, -308, -292, -276, -260,
        -244, -228, -212, -196, -180, -164, -148, -132,
        -120, -112, -104, -96, -88, -80, -72, -64,
        -56, -48, -40, -32, -24, -16, -8, 0,
        32124, 31100, 30076, 29052, 28028, 27004, 25980, 24956,
        23932, 22908, 21884, 20860, 19836, 18812, 17788, 16764,
        15996, 15484, 14972, 14460, 13948, 13436, 12924, 12412,
        11900, 11388, 10876, 10364, 9852, 9340, 8828, 8316,
        7932, 7676, 7420, 7164, 6908, 6652, 6396, 6140,
        5884, 5628, 5372, 5116, 4860, 4604, 4348, 4092,
        3900, 3772, 3644, 3516, 3388, 3260, 3132, 3004,
        2876, 2748, 2620, 2492, 2364, 2236, 2108, 1980,
        1884, 1820, 1756, 1692, 1628, 1564, 1500, 1436,
        1372, 1308, 1244, 1180, 1116, 1052, 988, 924,
        876, 844, 812, 780, 748, 716, 684, 652,
        620, 588, 556, 524, 492, 460, 428, 396,
        372, 356, 340, 324, 308, 292, 276, 260,
        244, 228, 212, 196, 180, 164, 148, 132,
        120, 112, 104, 96, 88, 80, 72, 64,
        56, 48, 40, 32, 24, 16, 8, 0
    };

    // 指数查找表（来源 Sun/CCITT 参考实现），用于快速确定段位
    private static final int[] EXP_LUT = new int[] {
        0,0,1,1,2,2,2,2,3,3,3,3,3,3,3,3,
        4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
        5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
        5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
        6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
        6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
        6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
        6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
        7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
        7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
        7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
        7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
        7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
        7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
        7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
        7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7
    };

    private G711UUtil() {
    }

    /**
     * PCM(16-bit) -> μ-law（默认小端序，常见于 WAV/Netty 缓冲）。
     * 尾部奇数字节会被忽略。
     */
    public static byte[] encode(byte[] pcmData) {
        return encode(pcmData, true);
    }

    /**
     * PCM(16-bit) -> μ-law，支持指定端序。
     * @param pcmData 原始 PCM
     * @param littleEndian true 表示低字节在前；false 表示高字节在前
     */
    public static byte[] encode(byte[] pcmData, boolean littleEndian) {
        if (pcmData == null || pcmData.length < 2) {
            return new byte[0];
        }
        int count = pcmData.length / 2;
        byte[] output = new byte[count];
        for (int i = 0, j = 0; i < count; i++, j += 2) {
            short pcm = littleEndian
                ? littleEndianToShort(pcmData[j], pcmData[j + 1])
                : bigEndianToShort(pcmData[j], pcmData[j + 1]);
            output[i] = linearToUlaw(pcm);
        }
        return output;
    }

    /**
     * PCM(16-bit) -> μ-law（ByteBuf 版本，读取推进 readerIndex）。
     * @param littleEndian true=小端；false=大端
     */
    public static ByteBuf encode(ByteBuf input, boolean littleEndian) {
        if (input == null || input.readableBytes() < 2) {
            return ByteBufAllocator.DEFAULT.buffer(0);
        }
        int count = input.readableBytes() / 2;
        ByteBuf output = ByteBufAllocator.DEFAULT.buffer(count);
        for (int i = 0; i < count; i++) {
            byte b0 = input.readByte();
            byte b1 = input.readByte();
            short pcm = littleEndian ? littleEndianToShort(b0, b1) : bigEndianToShort(b0, b1);
            output.writeByte(linearToUlaw(pcm));
        }
        return output;
    }

    /**
     * 兼容旧接口，默认按小端编码。
     */
    public static ByteBuf encode(ByteBuf input) {
        return encode(input, true);
    }

    /**
     * μ-law -> PCM(16-bit 小端序)。
     */
    public static byte[] decode(byte[] ulawData) {
        if (ulawData == null || ulawData.length == 0) {
            return new byte[0];
        }
        byte[] output = new byte[ulawData.length * 2];
        for (int i = 0, j = 0; i < ulawData.length; i++, j += 2) {
            short pcm = ULAW_TO_LINEAR[ulawData[i] & 0xFF];
            output[j] = (byte) (pcm & 0xFF);
            output[j + 1] = (byte) (pcm >> 8);
        }
        return output;
    }

    /**
     * μ-law -> PCM(16-bit 小端序)（ByteBuf 版本，读取推进 readerIndex）。
     */
    public static ByteBuf decode(ByteBuf input) {
        if (input == null || input.readableBytes() == 0) {
            return ByteBufAllocator.DEFAULT.buffer(0);
        }
        int length = input.readableBytes();
        ByteBuf output = ByteBufAllocator.DEFAULT.buffer(length * 2);
        for (int i = 0; i < length; i++) {
            short pcm = ULAW_TO_LINEAR[input.readByte() & 0xFF];
            output.writeByte((byte) (pcm & 0xFF));
            output.writeByte((byte) (pcm >> 8));
        }
        return output;
    }

    /**
     * 单个样本：线性 PCM -> μ-law。
     */
    public static byte encodeSample(short pcm) {
        return linearToUlaw(pcm);
    }

    /**
     * 单个样本：μ-law -> 线性 PCM。
     */
    public static short decodeSample(byte ulaw) {
        return ULAW_TO_LINEAR[ulaw & 0xFF];
    }

    private static short littleEndianToShort(byte low, byte high) {
        return (short) ((low & 0xFF) | (high << 8));
    }

    private static short bigEndianToShort(byte high, byte low) {
        return (short) ((high << 8) | (low & 0xFF));
    }

    /**
     * 参考 ITU-T G.711 / Sun 公开实现的 μ-law 编码。
     */
    private static byte linearToUlaw(short pcm) {
        int sample = pcm;
        int sign = (sample >> 8) & 0x80; // 0x80 表示负数

        if (sign != 0) {
            sample = -sample;
        }
        if (sample > CLIP) {
            sample = CLIP;
        }
        sample += BIAS;

        // exponent：根据高位快速确定段位；mantissa：段内 4bit
        int exponent = EXP_LUT[(sample >> 7) & 0xFF];
        int mantissa = (sample >> (exponent + 3)) & 0x0F;

        int uVal = ~(sign | (exponent << 4) | mantissa);
        return (byte) uVal;
    }

    /**
     * μ-law 码字 -> 线性 PCM 由查表完成，无需函数。
     */
}


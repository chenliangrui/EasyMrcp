package com.example.easymrcp.rtp;

public class G711uDecoder {
    private static final short[] ULAW_TABLE = new short[256];

    static {
        // 初始化μ-law解码表（示例值）
        for (int i=0; i<256; i++) {
            int sign = ((i & 0x80) == 0) ? -1 : 1;
            int exponent = (i >> 4) & 0x07;
            int mantissa = i & 0x0F;
            ULAW_TABLE[i] = (short)(sign * (33 << exponent) + mantissa * (1 << exponent));
        }
    }

    public static short[] decode(byte[] g711Data) {
        short[] pcm = new short[g711Data.length];
        for (int i=0; i<g711Data.length; i++) {
            pcm[i] = ULAW_TABLE[g711Data[i] & 0xFF];
        }
        return pcm;
    }
}

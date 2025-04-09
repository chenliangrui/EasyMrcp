package com.example.easymrcp.rtp;

public class G711uDecoder {
    // G.711U解码表（U-law转16位PCM）
    private static final short[] ULAW_TABLE = new short[256];

    static {
        for (int i = 0; i < 256; i++) {
            int ulaw = ~i; // U-law是补码存储
            int exponent = (ulaw >> 4) & 0x07;
            int mantissa = ulaw & 0x0F;
            short sample = (short) (((mantissa << 3) + 0x84) << exponent);
            if ((ulaw & 0x80) != 0) sample = (short) -sample;
            ULAW_TABLE[i] = sample;
        }
    }

    // 解码G.711U字节流为PCM
    public static byte[] decodeG711U(byte[] g711Data) {
        byte[] pcmData = new byte[g711Data.length * 2];
        for (int i = 0; i < g711Data.length; i++) {
            short sample = ULAW_TABLE[g711Data[i] & 0xFF];
            pcmData[i * 2] = (byte) (sample & 0xFF);
            pcmData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcmData;
    }
}

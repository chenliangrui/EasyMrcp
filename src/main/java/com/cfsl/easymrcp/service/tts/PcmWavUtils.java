package com.cfsl.easymrcp.service.tts;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 厂商合成音频的格式归一化工具。
 */
final class PcmWavUtils {
    private PcmWavUtils() {
    }

    static byte[] normalizeTo8kWav(byte[] source, String reSample) {
        return normalizeTo8kWav(source, reSample, 0, 0);
    }

    static byte[] normalizeTo8kWav(byte[] source, String reSample, int skipBytesInTheEndPacket) {
        return normalizeTo8kWav(source, reSample, 0, skipBytesInTheEndPacket);
    }

    /**
     * 兼容厂商返回的裸 PCM 和 WAV，裁剪首尾静音后统一输出 8kHz 单声道 WAV。
     */
    static byte[] normalizeTo8kWav(byte[] source, String reSample,
                                    int skipBytesInTheFirstPacket, int skipBytesInTheEndPacket) {
        byte[] pcm = stripWavHeader(source);
        int start = Math.min(Math.max(skipBytesInTheFirstPacket, 0), pcm.length);
        int end = Math.max(start, pcm.length - Math.max(skipBytesInTheEndPacket, 0));
        if (start != 0 || end != pcm.length) {
            byte[] trimmed = new byte[end - start];
            System.arraycopy(pcm, start, trimmed, 0, trimmed.length);
            pcm = trimmed;
        }
        if ("downsample24kTo8k".equals(reSample)) {
            pcm = downsample24kTo8k(pcm);
        }
        return wrapPcm(pcm, 8000, 1, 16);
    }

    private static byte[] stripWavHeader(byte[] source) {
        if (source.length < 12 || !"RIFF".equals(ascii(source, 0, 4))
                || !"WAVE".equals(ascii(source, 8, 4))) {
            return source;
        }
        int offset = 12;
        while (offset + 8 <= source.length) {
            String chunk = ascii(source, offset, 4);
            int length = ByteBuffer.wrap(source, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int dataOffset = offset + 8;
            if ("data".equals(chunk) && length >= 0 && dataOffset + length <= source.length) {
                byte[] pcm = new byte[length];
                System.arraycopy(source, dataOffset, pcm, 0, length);
                return pcm;
            }
            offset = dataOffset + length + (length & 1);
        }
        throw new IllegalArgumentException("TTS 返回的 WAV 缺少 data 块");
    }

    private static byte[] downsample24kTo8k(byte[] input) {
        int sampleCount = input.length / 2;
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length / 3);
        for (int sample = 0; sample < sampleCount; sample += 3) {
            int offset = sample * 2;
            output.write(input[offset]);
            output.write(input[offset + 1]);
        }
        return output.toByteArray();
    }

    private static byte[] wrapPcm(byte[] pcm, int sampleRate, int channels, int bits) {
        int byteRate = sampleRate * channels * bits / 8;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) (channels * bits / 8));
        header.putShort((short) bits);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(pcm.length);
        byte[] wav = new byte[44 + pcm.length];
        System.arraycopy(header.array(), 0, wav, 0, 44);
        System.arraycopy(pcm, 0, wav, 44, pcm.length);
        return wav;
    }

    private static String ascii(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }
}

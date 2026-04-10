package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.rtp.AudioCodecUtil;
import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyTtsRtpProcessor4Tests {

    @Test
    void processOnce_shouldEncodeAndAppendEndFlag() throws Exception {
        NettyTtsRtpProcessor4 processor = new NettyTtsRtpProcessor4("127.0.0.1", 9000, AudioCodecUtil.PT_PCMA);
        byte[] data = buildPcmWithEndFlag();
        processor.putData(data, data.length);

        processor.processOnce();

        ByteBuf output = outputBuffer(processor).readAll();
        try {
            assertTrue(output.readableBytes() >= 2);
            assertEquals(TTSConstant.TTS_END_BYTE, output.getByte(output.readableBytes() - 2));
            assertEquals(TTSConstant.TTS_END_BYTE, output.getByte(output.readableBytes() - 1));
        } finally {
            output.release();
            processor.releaseResources();
        }
    }

    @Test
    void processOnce_shouldTrimTailBeforeEncodingWhenEndFlagArrives() throws Exception {
        NettyTtsRtpProcessor4 processor = new NettyTtsRtpProcessor4("127.0.0.1", 9001, AudioCodecUtil.PT_PCMA);
        processor.setSkipBytesInTheEndPacket(320);
        byte[] data = buildPcmWithTailAndEndFlag();
        processor.putData(data, data.length);

        processor.processOnce();

        ByteBuf output = outputBuffer(processor).readAll();
        try {
            assertTrue(output.readableBytes() > 2);
            assertEquals(TTSConstant.TTS_END_BYTE, output.getByte(output.readableBytes() - 2));
            assertEquals(TTSConstant.TTS_END_BYTE, output.getByte(output.readableBytes() - 1));
        } finally {
            output.release();
            processor.releaseResources();
        }
    }

    @Test
    void interrupt_shouldClearBothBuffersAndWriteInterruptFlagToOutput() throws Exception {
        NettyTtsRtpProcessor4 processor = new NettyTtsRtpProcessor4("127.0.0.1", 9002, AudioCodecUtil.PT_PCMA);
        byte[] data = buildRawPcm(640);
        processor.putData(data, data.length);
        processor.interrupt();

        assertEquals(0, inputBuffer(processor).getSize());
        assertEquals(2, outputBuffer(processor).getSize());

        ByteBuf output = outputBuffer(processor).readAll();
        try {
            assertEquals(TTSConstant.TTS_INTERRUPT_BYTE, output.getByte(0));
            assertEquals(TTSConstant.TTS_INTERRUPT_BYTE, output.getByte(1));
        } finally {
            output.release();
            processor.releaseResources();
        }
    }

    private byte[] buildPcmWithEndFlag() {
        byte[] pcm = buildRawPcm(640);
        byte[] result = new byte[pcm.length + 2];
        System.arraycopy(pcm, 0, result, 0, pcm.length);
        result[result.length - 2] = TTSConstant.TTS_END_BYTE;
        result[result.length - 1] = TTSConstant.TTS_END_BYTE;
        return result;
    }

    private byte[] buildPcmWithTailAndEndFlag() {
        byte[] pcm = buildRawPcm(960);
        byte[] result = new byte[pcm.length + 2];
        System.arraycopy(pcm, 0, result, 0, pcm.length);
        result[result.length - 2] = TTSConstant.TTS_END_BYTE;
        result[result.length - 1] = TTSConstant.TTS_END_BYTE;
        return result;
    }

    private byte[] buildRawPcm(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i += 2) {
            data[i] = 0x01;
            if (i + 1 < size) {
                data[i + 1] = 0x00;
            }
        }
        return data;
    }

    private NettyAudioRingBuffer inputBuffer(NettyTtsRtpProcessor4 processor) {
        return processor.getInputRingBuffer();
    }

    private NettyAudioRingBuffer outputBuffer(NettyTtsRtpProcessor4 processor) {
        return processor.getOutputRingBuffer();
    }
}

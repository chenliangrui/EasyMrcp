package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.rtp.AudioCodecUtil;
import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import com.cfsl.easymrcp.tts.scheduler.TtsProcessScheduler;
import com.cfsl.easymrcp.tts.scheduler.TtsRtpScheduler;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyTtsRtpProcessorTests {

    @Test
    void processOnce_shouldEncodeAndAppendEndFlag() throws Exception {
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9000, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
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
    void processOnce_shouldPassThroughPcmWhenSessionUsesL16() throws Exception {
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9005, 97, EMConstant.VOIP_L16_BYTES_PER_FRAME, 20);
        byte[] data = buildPcmWithEndFlag();
        processor.putData(data, data.length);

        processor.processOnce();

        ByteBuf output = outputBuffer(processor).readAll();
        try {
            byte[] actual = new byte[output.readableBytes()];
            output.getBytes(output.readerIndex(), actual);
            assertEquals(data.length, actual.length);
            assertArrayEquals(data, actual);
        } finally {
            output.release();
            processor.releaseResources();
        }
    }

    @Test
    void processOnce_shouldTrimTailBeforeEncodingWhenEndFlagArrives() throws Exception {
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9001, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
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
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9002, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
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

    @Test
    void startRtpSender_shouldRegisterBothSchedulers() throws Exception {
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9003, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
        RecordingProcessScheduler processScheduler = new RecordingProcessScheduler();
        RecordingRtpScheduler rtpScheduler = new RecordingRtpScheduler();
        setField(processor, "processScheduler", processScheduler);
        setField(processor, "rtpScheduler", rtpScheduler);

        try {
            processor.startRtpSender();

            assertEquals(1, processScheduler.registerCount);
            assertEquals(1, rtpScheduler.registerCount);
            assertNotEquals("pending-rtp-scheduler", schedulerTaskId(processor));
        } finally {
            processor.stopRtpSender();
            processor.releaseResources();
        }
    }

    @Test
    void stopRtpSender_shouldCancelBothSchedulers() throws Exception {
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9004, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
        RecordingProcessScheduler processScheduler = new RecordingProcessScheduler();
        RecordingRtpScheduler rtpScheduler = new RecordingRtpScheduler();
        setField(processor, "processScheduler", processScheduler);
        setField(processor, "rtpScheduler", rtpScheduler);

        processor.startRtpSender();
        processor.stopRtpSender();

        try {
            assertEquals(1, processScheduler.cancelCount);
            assertEquals(1, rtpScheduler.cancelCount);
            assertNull(processTaskId(processor));
            assertNull(schedulerTaskId(processor));
        } finally {
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

    private String processTaskId(NettyTtsRtpProcessor processor) throws Exception {
        return (String) getField(processor, "processTaskId");
    }

    private String schedulerTaskId(NettyTtsRtpProcessor processor) throws Exception {
        return (String) getField(processor, "schedulerTaskId");
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private NettyAudioRingBuffer inputBuffer(NettyTtsRtpProcessor processor) {
        return processor.getInputRingBuffer();
    }

    private NettyAudioRingBuffer outputBuffer(NettyTtsRtpProcessor processor) {
        return processor.getOutputRingBuffer();
    }

    private static class RecordingProcessScheduler extends TtsProcessScheduler {
        private int registerCount;
        private int cancelCount;

        @Override
        public String register(NettyTtsRtpProcessor processor) {
            registerCount++;
            return "process-task";
        }

        @Override
        public void cancel(String taskId) {
            cancelCount++;
        }
    }

    private static class RecordingRtpScheduler extends TtsRtpScheduler {
        private int registerCount;
        private int cancelCount;

        @Override
        public String register(NettyTtsRtpProcessor processor, java.util.function.Consumer<String> callback) {
            registerCount++;
            return "rtp-task";
        }

        @Override
        public void cancel(String taskId) {
            cancelCount++;
        }
    }
}

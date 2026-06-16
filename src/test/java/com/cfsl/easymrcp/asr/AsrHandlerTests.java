package com.cfsl.easymrcp.asr;

import com.cfsl.easymrcp.domain.AsrConfig;
import com.cfsl.easymrcp.rtp.AudioCodecUtil;
import com.cfsl.easymrcp.rtp.NettyAsrRtpProcessor;
import com.cfsl.easymrcp.vad.VadHandle;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsrHandlerTests {

    @Test
    void receive_shouldUse8kVadForDictationWithoutResample() {
        AsrHandler handler = new TestAsrHandler();
        AsrConfig config = new AsrConfig();
        config.setIdentifyPatterns(ASRConstant.IDENTIFY_PATTERNS_DICTATION);
        handler.setConfig(config);
        ReflectionTestUtils.setField(
                handler,
                "nettyAsrRtpProcessor",
                new NettyAsrRtpProcessor(AudioCodecUtil.PT_PCMA, 160, 20));

        handler.receive();

        VadHandle vadHandle = (VadHandle) ReflectionTestUtils.getField(handler, "vadHandle");
        assertEquals(8000, ReflectionTestUtils.getField(vadHandle, "sampleRate"));
    }

    @Test
    void receive_shouldUse16kVadForDictationWithUpsample() {
        AsrHandler handler = new TestAsrHandler();
        AsrConfig config = new AsrConfig();
        config.setIdentifyPatterns(ASRConstant.IDENTIFY_PATTERNS_DICTATION);
        config.setReSample("upsample8kTo16k");
        handler.setConfig(config);
        ReflectionTestUtils.setField(
                handler,
                "nettyAsrRtpProcessor",
                new NettyAsrRtpProcessor(AudioCodecUtil.PT_PCMA, 160, 20));

        handler.receive();

        VadHandle vadHandle = (VadHandle) ReflectionTestUtils.getField(handler, "vadHandle");
        assertEquals(16000, ReflectionTestUtils.getField(vadHandle, "sampleRate"));
    }

    private static final class TestAsrHandler extends AsrHandler {
        @Override
        public void create() {
        }

        @Override
        public void receive(byte[] pcmData) {
        }

        @Override
        public void sendEof() {
        }

        @Override
        public void asrClose() {
        }
    }
}

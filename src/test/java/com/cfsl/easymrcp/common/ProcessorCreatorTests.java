package com.cfsl.easymrcp.common;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.asr.example.ExampleAsrConfig;
import com.cfsl.easymrcp.rtp.RtpAsrProperties;
import com.cfsl.easymrcp.rtp.RtpManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProcessorCreatorTests {

    @Test
    void getAsrHandler_shouldApplyGlobalRtpAsrProperties() {
        ProcessorCreator processorCreator = new ProcessorCreator();
        ExampleAsrConfig exampleAsrConfig = new ExampleAsrConfig();
        RtpAsrProperties rtpAsrProperties = new RtpAsrProperties();
        rtpAsrProperties.setReorderWindowPackets(7);
        rtpAsrProperties.setMaxConsecutiveLossFill(9);

        ReflectionTestUtils.setField(processorCreator, "asrMode", EMConstant.EXAMPLE_ASR);
        ReflectionTestUtils.setField(processorCreator, "exampleAsrConfig", exampleAsrConfig);
        ReflectionTestUtils.setField(processorCreator, "rtpAsrProperties", rtpAsrProperties);
        ReflectionTestUtils.setField(processorCreator, "rtpManager", new RtpManager());

        AsrHandler asrHandler = processorCreator.getAsrHandler();

        assertNotNull(asrHandler);
        assertEquals(7, asrHandler.getReorderWindowPackets());
        assertEquals(9, asrHandler.getMaxConsecutiveLossFill());
    }
}

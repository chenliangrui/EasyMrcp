package com.cfsl.easymrcp.common;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.asr.aliyunfunasr.AliyunFunasrConfig;
import com.cfsl.easymrcp.asr.aliyunfunasr.dictation.AliyunFunasrDictationProcessor;
import com.cfsl.easymrcp.asr.aliyunfunasr.transliterate.AliyunFunasrTransliterateProcessor;
import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.asr.example.ExampleAsrConfig;
import com.cfsl.easymrcp.asr.tencentcloud.TxCloudAsrConfig;
import com.cfsl.easymrcp.asr.tencentcloud.TxCloudAsrProcessor;
import com.cfsl.easymrcp.rtp.RtpAsrProperties;
import com.cfsl.easymrcp.rtp.RtpManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    @Test
    void getAsrHandler_shouldCreateAliyunFunasrDictationProcessor() {
        ProcessorCreator processorCreator = new ProcessorCreator();
        AliyunFunasrConfig aliyunFunasrConfig = new AliyunFunasrConfig();
        RtpAsrProperties rtpAsrProperties = new RtpAsrProperties();
        rtpAsrProperties.setReorderWindowPackets(11);
        rtpAsrProperties.setMaxConsecutiveLossFill(13);
        aliyunFunasrConfig.setIdentifyPatterns(ASRConstant.IDENTIFY_PATTERNS_DICTATION);

        ReflectionTestUtils.setField(processorCreator, "asrMode", EMConstant.ALIYUN_FUNASR);
        ReflectionTestUtils.setField(processorCreator, "aliyunFunasrConfig", aliyunFunasrConfig);
        ReflectionTestUtils.setField(processorCreator, "rtpAsrProperties", rtpAsrProperties);
        ReflectionTestUtils.setField(processorCreator, "rtpManager", new RtpManager());

        AsrHandler asrHandler = processorCreator.getAsrHandler();

        assertNotNull(asrHandler);
        assertInstanceOf(AliyunFunasrDictationProcessor.class, asrHandler);
        assertEquals(ASRConstant.IDENTIFY_PATTERNS_DICTATION, asrHandler.getIdentifyPatterns());
        assertEquals(11, asrHandler.getReorderWindowPackets());
        assertEquals(13, asrHandler.getMaxConsecutiveLossFill());
    }

    @Test
    void getAsrHandler_shouldCreateAliyunFunasrTransliterateProcessor() {
        ProcessorCreator processorCreator = new ProcessorCreator();
        AliyunFunasrConfig aliyunFunasrConfig = new AliyunFunasrConfig();
        RtpAsrProperties rtpAsrProperties = new RtpAsrProperties();
        rtpAsrProperties.setReorderWindowPackets(17);
        rtpAsrProperties.setMaxConsecutiveLossFill(19);
        aliyunFunasrConfig.setIdentifyPatterns(ASRConstant.IDENTIFY_PATTERNS_TRANSLITERATE);

        ReflectionTestUtils.setField(processorCreator, "asrMode", EMConstant.ALIYUN_FUNASR);
        ReflectionTestUtils.setField(processorCreator, "aliyunFunasrConfig", aliyunFunasrConfig);
        ReflectionTestUtils.setField(processorCreator, "rtpAsrProperties", rtpAsrProperties);
        ReflectionTestUtils.setField(processorCreator, "rtpManager", new RtpManager());

        AsrHandler asrHandler = processorCreator.getAsrHandler();

        assertNotNull(asrHandler);
        assertInstanceOf(AliyunFunasrTransliterateProcessor.class, asrHandler);
        assertEquals(ASRConstant.IDENTIFY_PATTERNS_TRANSLITERATE, asrHandler.getIdentifyPatterns());
        assertEquals(17, asrHandler.getReorderWindowPackets());
        assertEquals(19, asrHandler.getMaxConsecutiveLossFill());
    }

    @Test
    void getAsrHandler_shouldCreateTencentProcessorFromTencentIdentifyPattern() {
        ProcessorCreator processorCreator = new ProcessorCreator();
        TxCloudAsrConfig txCloudAsrConfig = new TxCloudAsrConfig();
        ExampleAsrConfig exampleAsrConfig = new ExampleAsrConfig();
        RtpAsrProperties rtpAsrProperties = new RtpAsrProperties();
        rtpAsrProperties.setReorderWindowPackets(23);
        rtpAsrProperties.setMaxConsecutiveLossFill(29);
        txCloudAsrConfig.setIdentifyPatterns(ASRConstant.IDENTIFY_PATTERNS_DICTATION);

        ReflectionTestUtils.setField(processorCreator, "asrMode", EMConstant.TENCENT_CLOUD);
        ReflectionTestUtils.setField(processorCreator, "txCloudAsrConfig", txCloudAsrConfig);
        ReflectionTestUtils.setField(processorCreator, "exampleAsrConfig", exampleAsrConfig);
        ReflectionTestUtils.setField(processorCreator, "rtpAsrProperties", rtpAsrProperties);
        ReflectionTestUtils.setField(processorCreator, "rtpManager", new RtpManager());

        AsrHandler asrHandler = processorCreator.getAsrHandler();

        assertNotNull(asrHandler);
        assertInstanceOf(TxCloudAsrProcessor.class, asrHandler);
        assertEquals(ASRConstant.IDENTIFY_PATTERNS_DICTATION, asrHandler.getIdentifyPatterns());
        assertEquals(23, asrHandler.getReorderWindowPackets());
        assertEquals(29, asrHandler.getMaxConsecutiveLossFill());
    }
}

package com.cfsl.easymrcp.common;

import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.asr.aliyunfunasr.AliyunFunasrConfig;
import com.cfsl.easymrcp.asr.aliyunfunasr.dictation.AliyunFunasrDictationProcessor;
import com.cfsl.easymrcp.asr.aliyunfunasr.transliterate.AliyunFunasrTransliterateProcessor;
import com.cfsl.easymrcp.asr.example.ExampleAsrConfig;
import com.cfsl.easymrcp.asr.example.ExampleAsrProcessor;
import com.cfsl.easymrcp.asr.funasr.FunAsrProcessor;
import com.cfsl.easymrcp.asr.funasr.FunasrConfig;
import com.cfsl.easymrcp.asr.tencentcloud.TxCloudAsrConfig;
import com.cfsl.easymrcp.asr.tencentcloud.TxCloudAsrProcessor;
import com.cfsl.easymrcp.asr.xfyun.XfyunAsrConfig;
import com.cfsl.easymrcp.asr.xfyun.dictation.XfyunDictationAsrProcessor;
import com.cfsl.easymrcp.asr.xfyun.transliterate.XfyunTransliterateAsrProcessor;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.rtp.RtpAsrProperties;
import com.cfsl.easymrcp.rtp.RtpManager;
import com.cfsl.easymrcp.tts.TtsEngine;
import com.cfsl.easymrcp.tts.TtsProcessor;
import com.cfsl.easymrcp.tts.aliyun.AliyunCosyVoiceEngine;
import com.cfsl.easymrcp.tts.aliyun.AliyunTtsConfig;
import com.cfsl.easymrcp.tts.example.ExampleTtsConfig;
import com.cfsl.easymrcp.tts.example.ExampleTtsProcessor;
import com.cfsl.easymrcp.tts.kokoro.KokoroConfig;
import com.cfsl.easymrcp.tts.kokoro.KokoroProcessor;
import com.cfsl.easymrcp.tts.TtsHandler;
import com.cfsl.easymrcp.tts.tencentcloud.TxCloudTtsConfig;
import com.cfsl.easymrcp.tts.tencentcloud.TxCloudTtsProcessor;
import com.cfsl.easymrcp.tts.xfyun.XfyunTtsConfig;
import com.cfsl.easymrcp.tts.xfyun.XfyunTtsProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * 根据配置决定加载某个asr或tts
 */
@Component
public class ProcessorCreator {
    private static final Logger log = LoggerFactory.getLogger(ProcessorCreator.class);
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    @Value("${mrcp.ttsMode}")
    String ttsMode;
    
    @Autowired
    FunasrConfig funasrConfig;
    @Autowired
    AliyunFunasrConfig aliyunFunasrConfig;
    @Autowired
    AliyunTtsConfig aliyunTtsConfig;
    @Autowired
    KokoroConfig kokoroConfig;
    @Autowired
    XfyunAsrConfig xfyunAsrConfig;
    @Autowired
    ExampleAsrConfig exampleAsrConfig;
    @Autowired
    XfyunTtsConfig xfyunTtsConfig;
    @Autowired
    TxCloudAsrConfig txCloudAsrConfig;
    @Autowired
    TxCloudTtsConfig txCloudTtsConfig;
    @Autowired
    ExampleTtsConfig exampleTtsConfig;
    @Autowired
    RtpManager rtpManager;
    @Autowired
    RtpAsrProperties rtpAsrProperties;
    @Autowired
    MrcpManage mrcpManage;

    public AsrHandler getAsrHandler(String selectedAsrMode) {
        AsrHandler asrHandler = createAsrHandler(selectedAsrMode);
        if (asrHandler != null) {
            asrHandler.setAsrEngine(selectedAsrMode);
            asrHandler.setRtpManager(rtpManager);
            asrHandler.setReorderWindowPackets(rtpAsrProperties.getReorderWindowPackets());
            asrHandler.setMaxConsecutiveLossFill(rtpAsrProperties.getMaxConsecutiveLossFill());
        }
        return asrHandler;
    }
    
    private AsrHandler createAsrHandler(String selectedAsrMode) {
        switch (selectedAsrMode) {
            case EMConstant.FUNASR:
                FunAsrProcessor funAsrProcessor = new FunAsrProcessor(funasrConfig);
                funAsrProcessor.setConfig(funasrConfig);
                return funAsrProcessor;
            case EMConstant.ALIYUN_FUNASR:
                if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(aliyunFunasrConfig.getIdentifyPatterns())) {
                    AliyunFunasrDictationProcessor aliyunFunasrDictationProcessor =
                            new AliyunFunasrDictationProcessor(aliyunFunasrConfig);
                    aliyunFunasrDictationProcessor.setConfig(aliyunFunasrConfig);
                    return aliyunFunasrDictationProcessor;
                } else if (ASRConstant.IDENTIFY_PATTERNS_TRANSLITERATE.equals(aliyunFunasrConfig.getIdentifyPatterns())) {
                    AliyunFunasrTransliterateProcessor aliyunFunasrTransliterateProcessor =
                            new AliyunFunasrTransliterateProcessor(aliyunFunasrConfig);
                    aliyunFunasrTransliterateProcessor.setConfig(aliyunFunasrConfig);
                    return aliyunFunasrTransliterateProcessor;
                }
                break;
            case EMConstant.XFYUN:
                if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(xfyunAsrConfig.getIdentifyPatterns())) {
                    XfyunDictationAsrProcessor xfyunDictationAsrProcessor = new XfyunDictationAsrProcessor(xfyunAsrConfig);
                    xfyunDictationAsrProcessor.setConfig(xfyunAsrConfig);
                    return xfyunDictationAsrProcessor;
                } else if (ASRConstant.IDENTIFY_PATTERNS_TRANSLITERATE.equals(xfyunAsrConfig.getIdentifyPatterns())) {
                    XfyunTransliterateAsrProcessor xfyunTransliterateAsrProcessor = new XfyunTransliterateAsrProcessor(xfyunAsrConfig);
                    xfyunTransliterateAsrProcessor.setConfig(xfyunAsrConfig);
                    return xfyunTransliterateAsrProcessor;
                }
            case EMConstant.TENCENT_CLOUD:
                if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(txCloudAsrConfig.getIdentifyPatterns())) {
                    TxCloudAsrProcessor txCloudProcessor = new TxCloudAsrProcessor(txCloudAsrConfig);
                    txCloudProcessor.setConfig(txCloudAsrConfig);
                    return txCloudProcessor;
                }
                break;
            case EMConstant.EXAMPLE_ASR:
                ExampleAsrProcessor exampleProcessor = new ExampleAsrProcessor(exampleAsrConfig);
                exampleProcessor.setConfig(exampleAsrConfig);
                return exampleProcessor;
        }
        return null;
    }

    public TtsHandler getTtsHandler() {
        return new TtsHandler();
    }

    /**
     * 懒加载tts引擎，没有参数则使用配置文件中的默认值
     * @param id uuid
     * @return tts处理器
     */
    public TtsProcessor getTtsProcessor(String id) {
        TtsHandler ttsHandler = mrcpManage.getTtsHandler(id);
        TtsProcessor ttsProcessor = new TtsProcessor(executorService);
        ttsProcessor.setTtsHandler(ttsHandler);
        return ttsProcessor;
    }

    /**
     * 设置与厂商对接的tts引擎
     *
     * @param id
     * @param ttsProcessor
     * @param pre   是否预加载
     * @return
     */
    public TtsEngine createTtsEngine(String id, TtsProcessor ttsProcessor, String pre, String ttsEngineId) {
        TtsHandler ttsHandler = mrcpManage.getTtsHandler(id);
        String ttsEngineName = mrcpManage.getTtsEngineName(id);
        String voice = mrcpManage.getVoice(id);
        if (ttsEngineName == null || ttsEngineName.isEmpty()) {
            ttsEngineName = ttsMode;
        }
        TtsEngine ttsEngine = null;
        switch (ttsEngineName) {
            case EMConstant.ALIYUN:
                ttsEngine = new AliyunCosyVoiceEngine(aliyunTtsConfig);
                ttsHandler.setSkipBytesInTheEndPacket(aliyunTtsConfig.getSkipBytesInTheEndPacket());
                break;
            case EMConstant.KOKORO:
                ttsEngine = new KokoroProcessor(kokoroConfig);
                ttsHandler.setReSample(kokoroConfig.getReSample());
                break;
            case EMConstant.XFYUN:
                ttsEngine = new XfyunTtsProcessor(xfyunTtsConfig);
                break;
            case EMConstant.TENCENT_CLOUD:
                ttsEngine = new TxCloudTtsProcessor(txCloudTtsConfig);
                ttsHandler.setReSample(txCloudTtsConfig.getReSample());
                break;
            case EMConstant.EXAMPLE_TTS:
                ttsEngine = new ExampleTtsProcessor(exampleTtsConfig);
                ttsHandler.setReSample(exampleTtsConfig.getReSample());
                break;
            default:
                throw new RuntimeException("Unknown TTS mode: " + ttsMode);
        }
        int ttsVersion = ttsHandler.newTtsVersion(pre);
        ttsEngine.setTtsVersion(ttsVersion);
        ttsProcessor.setTtsEngine(ttsEngineId, ttsEngine);
        ttsEngine.setTtsHandler(ttsHandler);
        if (voice != null && !voice.isEmpty()) {
            ttsEngine.setVoice(voice);
        }
        ttsEngine.setId(ttsEngineId);
        return ttsEngine;
    }
}

package com.cfsl.easymrcp.common;

import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.asr.example.ExampleAsrConfig;
import com.cfsl.easymrcp.asr.example.ExampleAsrProcessor;
import com.cfsl.easymrcp.asr.funasr.FunAsrProcessor;
import com.cfsl.easymrcp.asr.funasr.FunasrConfig;
import com.cfsl.easymrcp.asr.tencentcloud.TxCloudAsrConfig;
import com.cfsl.easymrcp.asr.tencentcloud.TxCloudAsrProcessor;
import com.cfsl.easymrcp.asr.xfyun.XfyunAsrConfig;
import com.cfsl.easymrcp.asr.xfyun.dictation.XfyunDictationAsrProcessor;
import com.cfsl.easymrcp.asr.xfyun.transliterate.XfyunTransliterateAsrProcessor;
import com.cfsl.easymrcp.tts.example.ExampleTtsConfig;
import com.cfsl.easymrcp.tts.example.ExampleTtsProcessor;
import com.cfsl.easymrcp.tts.kokoro.KokoroConfig;
import com.cfsl.easymrcp.tts.kokoro.KokoroProcessor;
import com.cfsl.easymrcp.tts.TtsHandler;
import com.cfsl.easymrcp.tts.tencentcloud.TxCloudTtsConfig;
import com.cfsl.easymrcp.tts.tencentcloud.TxCloudTtsProcessor;
import com.cfsl.easymrcp.tts.xfyun.XfyunTtsConfig;
import com.cfsl.easymrcp.tts.xfyun.XfyunTtsProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 根据配置决定加载某个asr或tts
 */
@Component
public class ProcessorCreator {
    @Value("${mrcp.asrMode}")
    String asrMode;
    @Value("${mrcp.ttsMode}")
    String ttsMode;
    @Autowired
    FunasrConfig funasrConfig;
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
    TxCloudTtsConfig txCloudTtsConfig;;
    @Autowired
    ExampleTtsConfig exampleTtsConfig;

    public AsrHandler getAsrHandler() {
        switch (asrMode) {
            case "funasr":
                FunAsrProcessor funAsrProcessor = new FunAsrProcessor(funasrConfig);
                funAsrProcessor.setConfig(funasrConfig);
                return funAsrProcessor;
            case "xfyun":
                if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(xfyunAsrConfig.getIdentifyPatterns())) {
                    XfyunDictationAsrProcessor xfyunDictationAsrProcessor = new XfyunDictationAsrProcessor(xfyunAsrConfig);
                    xfyunDictationAsrProcessor.setConfig(xfyunAsrConfig);
                    return xfyunDictationAsrProcessor;
                } else if (ASRConstant.IDENTIFY_PATTERNS_TRANSLITERATE.equals(xfyunAsrConfig.getIdentifyPatterns())) {
                    XfyunTransliterateAsrProcessor xfyunTransliterateAsrProcessor = new XfyunTransliterateAsrProcessor(xfyunAsrConfig);
                    xfyunTransliterateAsrProcessor.setConfig(xfyunAsrConfig);
                    return xfyunTransliterateAsrProcessor;
                }
            case "tencent-cloud":
                if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(xfyunAsrConfig.getIdentifyPatterns())) {
                    TxCloudAsrProcessor txCloudProcessor = new TxCloudAsrProcessor(txCloudAsrConfig);
                    txCloudProcessor.setConfig(txCloudAsrConfig);
                    return txCloudProcessor;
                }
            case "example-asr":
                ExampleAsrProcessor exampleProcessor = new ExampleAsrProcessor(exampleAsrConfig);
                exampleProcessor.setConfig(exampleAsrConfig);
                return exampleProcessor;
        }
        return null;
    }

    public TtsHandler getTtsHandler() {
        switch (ttsMode) {
            case "kokoro":
                KokoroProcessor kokoroProcessor = new KokoroProcessor(kokoroConfig);
                kokoroProcessor.setConfig(kokoroConfig);
                return kokoroProcessor;
            case "xfyun":
                XfyunTtsProcessor xfyunTtsProcessor = new XfyunTtsProcessor(xfyunTtsConfig);
                return xfyunTtsProcessor;
            case "tencent-cloud":
                TxCloudTtsProcessor txCloudAsrProcessor = new TxCloudTtsProcessor(txCloudTtsConfig);
                txCloudAsrProcessor.setConfig(txCloudTtsConfig);
                return txCloudAsrProcessor;
            case "example-tts":
                ExampleTtsProcessor exampleTtsProcessor = new ExampleTtsProcessor(exampleTtsConfig);
                exampleTtsProcessor.setConfig(exampleTtsConfig);
                return exampleTtsProcessor;
        }
        return null;
    }
}

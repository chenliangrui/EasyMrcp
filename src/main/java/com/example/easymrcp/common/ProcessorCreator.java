package com.example.easymrcp.common;

import com.example.easymrcp.asr.ASRConstant;
import com.example.easymrcp.asr.AsrHandler;
import com.example.easymrcp.asr.funasr.FunAsrProcessor;
import com.example.easymrcp.asr.funasr.FunasrConfig;
import com.example.easymrcp.asr.xfyun.XfyunAsrConfig;
import com.example.easymrcp.asr.xfyun.dictation.XfyunDictationAsrProcessor;
import com.example.easymrcp.asr.xfyun.transliterate.XfyunTransliterateAsrProcessor;
import com.example.easymrcp.tts.kokoro.KokoroConfig;
import com.example.easymrcp.tts.kokoro.KokoroProcessor;
import com.example.easymrcp.tts.TtsHandler;
import com.example.easymrcp.tts.xfyun.XfyunTtsConfig;
import com.example.easymrcp.tts.xfyun.XfyunTtsProcessor;
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
    XfyunTtsConfig xfyunTtsConfig;

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
        }
        return null;
    }
}

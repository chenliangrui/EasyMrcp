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
import com.example.easymrcp.tts.xfyun.XfyunTtsProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 根据配置决定加载某个asr
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

    public AsrHandler getAsrHandler() {
        if (asrMode.equals("funasr")) {
            FunAsrProcessor funAsrProcessor = new FunAsrProcessor(funasrConfig);
            if (funasrConfig.getIdentifyPatterns() != null && !funasrConfig.getIdentifyPatterns().isEmpty()) {
                funAsrProcessor.setIdentifyPatterns(funasrConfig.getIdentifyPatterns());
            }
            return funAsrProcessor;
        } else if (asrMode.equals("xfyun")) {
            if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(xfyunAsrConfig.getIdentifyPatterns())) {
                return new XfyunDictationAsrProcessor();
            } else if (ASRConstant.IDENTIFY_PATTERNS_TRANSLITERATE.equals(xfyunAsrConfig.getIdentifyPatterns())) {
                return new XfyunTransliterateAsrProcessor();
            }
        }
        return null;
    }

    public TtsHandler getTtsHandler() {
        if (ttsMode.equals("kokoro")) {
            KokoroProcessor kokoroProcessor = new KokoroProcessor(kokoroConfig);
            if (kokoroConfig.getReSample() != null && !kokoroConfig.getReSample().isEmpty()) {
                kokoroProcessor.setReSample(kokoroConfig.getReSample());
            }
            return kokoroProcessor;
        } else if (ttsMode.equals("xfyun")) {
            XfyunTtsProcessor xfyunTtsProcessor = new XfyunTtsProcessor();
            return xfyunTtsProcessor;
        }
        return null;
    }
}

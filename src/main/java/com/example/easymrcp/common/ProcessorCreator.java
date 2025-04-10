package com.example.easymrcp.common;

import com.example.easymrcp.asr.AsrHandler;
import com.example.easymrcp.asr.funasr.FunAsrProcessor;
import com.example.easymrcp.asr.funasr.FunasrConfig;
import com.example.easymrcp.tts.kokoro.KokoroConfig;
import com.example.easymrcp.tts.kokoro.KokoroProcessor;
import com.example.easymrcp.tts.TtsHandler;
import com.example.easymrcp.tts.xfyun.XfyunProcessor;
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

    public AsrHandler getAsrHandler() {
        if (asrMode.equals("funasr")) {
            return new FunAsrProcessor(funasrConfig);
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
            XfyunProcessor xfyunProcessor = new XfyunProcessor();
            return xfyunProcessor;
        }
        return null;
    }
}

package com.example.easymrcp.common;

import com.example.easymrcp.asr.AsrHandler;
import com.example.easymrcp.asr.FunAsrProcessor;
import com.example.easymrcp.tts.KokoroProcessor;
import com.example.easymrcp.tts.TtsHandler;
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

    public AsrHandler getAsrHandler() {
        if (asrMode.equals("funasr")) {
            return new FunAsrProcessor();
        }
        return null;
    }

    public TtsHandler getTtsHandler() {
        if (ttsMode.equals("kokoro")) {
            return new KokoroProcessor();
        }
        return null;
    }
}

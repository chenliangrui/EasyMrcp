package com.example.easymrcp.common;

import com.example.easymrcp.asr.AsrHandler;
import com.example.easymrcp.asr.FunAsrProcessor;
import com.example.easymrcp.tts.kokoro.KokoroProcessor;
import com.example.easymrcp.tts.TtsHandler;
import com.example.easymrcp.tts.xfyun.XfyunProcessor;
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
            KokoroProcessor kokoroProcessor = new KokoroProcessor();
            //TODO 设置降采样，后续移到配置文件中
            kokoroProcessor.setReSample("downsample24kTo8k");
            return kokoroProcessor;
        } else if (ttsMode.equals("xfyun")) {
            XfyunProcessor xfyunProcessor = new XfyunProcessor();
            return xfyunProcessor;
        }
        return null;
    }
}

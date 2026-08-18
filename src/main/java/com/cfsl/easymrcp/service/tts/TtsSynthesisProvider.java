package com.cfsl.easymrcp.service.tts;

/**
 * 发布阶段的厂商 TTS 合成接口，与实时通话的 TTS 播放链路无关。
 */
public interface TtsSynthesisProvider {
    /**
     * 返回 voiceConfig.ttsEngine 使用的引擎标识。
     */
    String getEngineName();

    /**
     * 调用厂商接口合成并返回 8kHz、16bit、单声道 WAV。
     */
    byte[] synthesize(String text, String voice);
}

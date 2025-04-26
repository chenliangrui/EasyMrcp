package com.example.easymrcp.asr;

public class ASRConstant {
    // asr语音识别模式: transliterate(长时间语音转写)
    public static final String IDENTIFY_PATTERNS_TRANSLITERATE = "transliterate";
    // asr语音识别模式: dictation(一句话语音识别)
    public static final String IDENTIFY_PATTERNS_DICTATION = "dictation";

    public static final byte TTS_END_BYTE = 111;
    public static final byte[] TTS_END_FLAG = new byte[] { TTS_END_BYTE, TTS_END_BYTE };
}

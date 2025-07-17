package com.cfsl.easymrcp.asr;

public class ASRConstant {
    // asr语音识别模式: transliterate(长时间语音转写)
    public static final String IDENTIFY_PATTERNS_TRANSLITERATE = "transliterate";
    // asr语音识别模式: dictation(一句话语音识别)
    public static final String IDENTIFY_PATTERNS_DICTATION = "dictation";

    // 是否启动asr超时计时器(布尔类型)
    public static final String StartInputTimers = "StartInputTimers";
    // asr识别开始后，多长时间内未检测到任何语音输入则超时(数字类型，毫秒值)
    public static final String NoInputTimeout = "NoInputTimeout";
    // 检测到语音后，静音持续多长时间被视为说话结束(数字类型，毫秒值)
    public static final String SpeechCompleteTimeout = "SpeechCompleteTimeout";
}

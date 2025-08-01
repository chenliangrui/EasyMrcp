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
    // 开启ASR识别后自动打断(布尔类型)
    public static final String AutomaticInterruption = "AutomaticInterruption";

    // 一句话语音识别或实时语音识别需要的action(打断)
    public static final String Interrupt = "Interrupt";
    // 一句话语音识别或实时语音识别需要的action(asr识别结果)
    public static final String Result = "Result";
}

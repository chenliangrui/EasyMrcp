package com.cfsl.easymrcp.tcp;

/**
 * TCP事件类型枚举
 */
public enum TcpEventType {
    /**
     * 客户端tcp建立连接事件
     */
    ClientConnect,

    /**
     * 客户端tcp关闭事件
     */
    ClientDisConnect,

    /**
     * 开始ASR事件
     */
    DetectSpeech,

    /**
     * ASR识别完成事件
     */
    RecognitionComplete,

    /**
     * Asr无用户语音等待超时事件
     */
    NoInputTimeout,

    /**
     * 暂停ASR识别
     */
    PauseDetectSpeech,

    /**
     * 恢复ASR识别
     */
    ResumeDetectSpeech,

    /**
     * TTS合成完成事件
     */
    SpeakComplete,

    /**
     * 进行TTS事件
     */
    Speak,

    /**
     * 进行打断事件
     */
    Interrupt,

    /**
     * TTS合成被打断事件
     */
    SpeakInterrupted,

    /**
     * 打断当前TTS并进行TTS合成事件
     */
    InterruptAndSpeak,

    /**
     * TTS静音事件
     */
    Silence,

    /**
     * 进行不可打断的TTS
     */
    SpeakWithNoInterrupt,

    /**
     * 实时推送asr识别结果
     */
    AsrRealTimeResult
}
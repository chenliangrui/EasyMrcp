package com.cfsl.easymrcp.tcp;

/**
 * TCP事件类型枚举
 */
public enum TcpEventType {

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
     * TTS合成完成事件
     */
    SpeakComplete,

    /**
     * 进行TTS事件
     */
    Speak,

    /**
     * TTS合成被打断事件
     */
    SpeakInterrupted,

    /**
     * 打断当前TTS并进行TTS合成事件
     */
    InterruptAndSpeak
}
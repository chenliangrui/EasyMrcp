package com.cfsl.easymrcp.examples.esl.client;

public enum TcpEventType {
    ClientConnect,
    ClientDisConnect,
    DetectSpeech,
    RecognitionComplete,
    NoInputTimeout,
    PauseDetectSpeech,
    ResumeDetectSpeech,
    SpeakComplete,
    Speak,
    Interrupt,
    SpeakInterrupted,
    InterruptAndSpeak,
    Silence,
    SpeakWithNoInterrupt,
    AsrRealTimeResult
}

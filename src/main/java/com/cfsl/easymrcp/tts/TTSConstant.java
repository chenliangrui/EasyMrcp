package com.cfsl.easymrcp.tts;

public class TTSConstant {
    public static final byte TTS_END_BYTE = 111;
    public static final byte TTS_INTERRUPT_BYTE = 112;
    public static final byte[] TTS_END_FLAG = new byte[] { TTS_END_BYTE, TTS_END_BYTE };
    public static final byte[] TTS_INTERRUPT_FLAG = new byte[] { TTS_INTERRUPT_BYTE, TTS_INTERRUPT_BYTE };
}

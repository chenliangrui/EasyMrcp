package com.cfsl.easymrcp.tts;

import io.netty.buffer.ByteBuf;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TtsEngine {
    @Setter
    protected TtsHandler ttsHandler;
    @Setter
    protected String voice;
    // 本次tts的版本
    @Setter
    private int ttsVersion = 0;

    public abstract void create();

    public abstract void speak(String text);

    /**
     * 关闭TTS资源
     */
    public abstract void ttsClose();

    protected void putAudioData(byte[] audioChunk, int bytesRead) {
        if (ttsVersion < ttsHandler.getTtsVersion()) return;
        this.ttsHandler.putAudioData(audioChunk, bytesRead);
    }

    protected void putAudioData(ByteBuf byteBuf) {
        if (ttsVersion < ttsHandler.getTtsVersion()) return;
        this.ttsHandler.putAudioData(byteBuf);
    }
}

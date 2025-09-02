package com.cfsl.easymrcp.tts;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.Setter;

@Setter
public abstract class TtsProcessor {
    protected TtsHandler ttsHandler;
    @Getter
    protected String voice;

    public abstract void create();

    public abstract void speak(String text);

    /**
     * 关闭TTS资源
     */
    public abstract void ttsClose();

    protected void putAudioData(byte[] audioChunk, int bytesRead) {
        this.ttsHandler.putAudioData(audioChunk, bytesRead);
    }

    protected void putAudioData(ByteBuf byteBuf) {
        this.ttsHandler.putAudioData(byteBuf);
    }
}

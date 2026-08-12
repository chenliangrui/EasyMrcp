package com.cfsl.easymrcp.domain;

/**
 * ASR recognition completion payload sent to the platform.
 */
public class RecognitionCompleteProtocol {
    private String text;
    private String asrEngine;
    private Long audioDurationMs;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAsrEngine() {
        return asrEngine;
    }

    public void setAsrEngine(String asrEngine) {
        this.asrEngine = asrEngine;
    }

    public Long getAudioDurationMs() {
        return audioDurationMs;
    }

    public void setAudioDurationMs(Long audioDurationMs) {
        this.audioDurationMs = audioDurationMs;
    }
}

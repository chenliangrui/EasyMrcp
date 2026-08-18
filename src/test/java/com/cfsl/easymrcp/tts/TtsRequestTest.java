package com.cfsl.easymrcp.tts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsRequestTest {
    @Test
    void keepsTextAndCacheAsSeparateFields() {
        TtsRequest cached = TtsRequest.parse("{\"text\":\"您好\",\"cache\":\"a.wav\"}");
        assertTrue(cached.hasCache());
        assertEquals("您好", cached.getText());
        assertEquals("a.wav", cached.getCache());

        TtsRequest text = TtsRequest.parse("普通文本");
        assertFalse(text.hasCache());
        assertEquals("普通文本", text.getText());
    }
}

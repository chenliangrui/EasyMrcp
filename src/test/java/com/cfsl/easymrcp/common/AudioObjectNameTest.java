package com.cfsl.easymrcp.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioObjectNameTest {
    @Test
    void parsesMetadataFromFileName() {
        String hash = "a".repeat(64);
        AudioObjectName value = AudioObjectName.parse(
                "tts-cache/agent-speak/20260817/1001_v12_c18_s182430_" + hash + ".wav",
                "tts-cache/agent-speak/");

        assertEquals(12L, value.getVersion());
        assertEquals(18, value.getCharCount());
        assertEquals(182430L, value.getSize());
        assertEquals(hash, value.getSha256());
    }

    @Test
    void rejectsUnexpectedPrefixAndTraversal() {
        String file = "1001_v1_c1_s44_" + "b".repeat(64) + ".wav";
        assertThrows(IllegalArgumentException.class,
                () -> AudioObjectName.parse("other/" + file, "tts-cache/agent-speak/"));
        assertThrows(IllegalArgumentException.class,
                () -> AudioObjectName.parse("tts-cache/agent-speak/../" + file,
                        "tts-cache/agent-speak/"));
    }

    @Test
    void sha256UsesFixedLowercaseHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                AudioCacheService.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }
}

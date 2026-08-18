package com.cfsl.easymrcp.service.tts;

import com.cfsl.easymrcp.common.AudioCacheService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcmWavUtilsTest {
    @Test
    void wrapsPcmAs8kMonoWav() {
        byte[] pcm = new byte[] {1, 2, 3, 4};
        byte[] wav = PcmWavUtils.normalizeTo8kWav(pcm, null);

        assertEquals(48, wav.length);
        assertTrue(AudioCacheService.isPcmWav(wav));
        assertEquals(4, AudioCacheService.extractPcm(wav).length);
    }

    @Test
    void downsamples24kPcmByThree() {
        byte[] pcm = new byte[12];
        byte[] wav = PcmWavUtils.normalizeTo8kWav(pcm, "downsample24kTo8k");

        assertEquals(4, AudioCacheService.extractPcm(wav).length);
    }

    @Test
    void trimsConfiguredVendorTailBytes() {
        byte[] pcm = new byte[104];
        byte[] wav = PcmWavUtils.normalizeTo8kWav(pcm, null, 100);

        assertEquals(4, AudioCacheService.extractPcm(wav).length);
    }
}

package com.cfsl.easymrcp.service.tts;

import com.cfsl.easymrcp.common.AudioCacheService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineTtsSynthesisServiceTest {
    @Test
    void routesRequestToVendorSynthesisService() {
        OfflineTtsSynthesisService service = new OfflineTtsSynthesisService(
                Collections.singletonList(new StubSynthesisProvider()));
        TtsSynthesizeRequest request = request("stub", "voice");

        byte[] wav = service.synthesize(request);

        assertTrue(AudioCacheService.isPcmWav(wav));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, AudioCacheService.extractPcm(wav));
    }

    @Test
    void rejectsUnsupportedEngine() {
        OfflineTtsSynthesisService service = new OfflineTtsSynthesisService(Collections.emptyList());

        assertThrows(IllegalArgumentException.class,
                () -> service.synthesize(request("unknown", "voice")));
    }

    private TtsSynthesizeRequest request(String engine, String voice) {
        TtsSynthesizeRequest request = new TtsSynthesizeRequest();
        request.setText("您好");
        TtsSynthesizeRequest.VoiceConfig voiceConfig = new TtsSynthesizeRequest.VoiceConfig();
        voiceConfig.setTtsEngine(engine);
        voiceConfig.setVoice(voice);
        request.setVoiceConfig(voiceConfig);
        return request;
    }

    private static class StubSynthesisProvider implements TtsSynthesisProvider {
        @Override
        public String getEngineName() {
            return "stub";
        }

        @Override
        public byte[] synthesize(String text, String voice) {
            return PcmWavUtils.normalizeTo8kWav(new byte[] {1, 2, 3, 4}, null);
        }
    }
}

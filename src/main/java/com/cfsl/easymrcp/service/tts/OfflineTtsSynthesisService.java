package com.cfsl.easymrcp.service.tts;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布阶段的 TTS 合成入口，按引擎标识分发到对应厂商实现。
 */
@Service
public class OfflineTtsSynthesisService {
    private final Map<String, TtsSynthesisProvider> providers = new HashMap<>();

    public OfflineTtsSynthesisService(List<TtsSynthesisProvider> providers) {
        for (TtsSynthesisProvider provider : providers) {
            this.providers.put(provider.getEngineName(), provider);
        }
    }

    public byte[] synthesize(TtsSynthesizeRequest request) {
        String engineName = request.getVoiceConfig().getTtsEngine();
        TtsSynthesisProvider provider = providers.get(engineName);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的 TTS 引擎: " + engineName);
        }
        return provider.synthesize(request.getText(), request.getVoiceConfig().getVoice());
    }
}

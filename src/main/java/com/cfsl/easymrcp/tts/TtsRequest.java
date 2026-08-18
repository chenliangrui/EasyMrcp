package com.cfsl.easymrcp.tts;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;

@Getter
public class TtsRequest {
    private final String text;
    private final String cache;

    public TtsRequest(String text, String cache) {
        this.text = text == null ? "" : text;
        this.cache = cache;
    }

    public static TtsRequest parse(String data) {
        if (data == null || !data.trim().startsWith("{")) {
            return new TtsRequest(data, null);
        }
        try {
            JSONObject payload = JSON.parseObject(data);
            if (payload.containsKey("text")) {
                return new TtsRequest(payload.getString("text"), payload.getString("cache"));
            }
        } catch (Exception ignored) {
            // 非法 JSON 继续按普通文本处理，保持原有 Speak 行为。
        }
        return new TtsRequest(data, null);
    }

    public boolean hasCache() {
        return cache != null && !cache.trim().isEmpty();
    }
}

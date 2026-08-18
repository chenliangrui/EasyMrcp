package com.cfsl.easymrcp.service.tts;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 发布开场白时调用 EasyMrcp 合成接口的请求参数。
 */
@Data
public class TtsSynthesizeRequest {
    @NotBlank
    private String text;
    @Valid
    @NotNull
    private VoiceConfig voiceConfig;

    /**
     * 平台保存的厂商引擎和音色配置。
     */
    @Data
    public static class VoiceConfig {
        private Long id;
        @NotBlank
        private String ttsEngine;
        private String voice;
    }
}

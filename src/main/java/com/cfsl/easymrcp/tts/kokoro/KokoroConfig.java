package com.cfsl.easymrcp.tts.kokoro;

import com.cfsl.easymrcp.domain.TtsConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "kokoro")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:tts/kokoro.properties", "file:tts/kokoro.properties"}, ignoreResourceNotFound = true)
public class KokoroConfig extends TtsConfig {
    private String apiUrl;
    private String model;
}

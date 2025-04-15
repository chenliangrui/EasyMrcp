package com.example.easymrcp.tts.kokoro;

import com.example.easymrcp.domain.BaseConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:tts/kokoro.properties", "file:tts/kokoro.properties"}, ignoreResourceNotFound = true)
public class KokoroConfig extends BaseConfig {
    private String apiUrl;
    private String model;
    private String voice;
}

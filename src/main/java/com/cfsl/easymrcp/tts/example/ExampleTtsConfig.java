package com.cfsl.easymrcp.tts.example;

import com.cfsl.easymrcp.domain.TtsConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "example-tts")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:tts/example-tts.properties", "file:tts/example-tts.properties"}, ignoreResourceNotFound = true)
public class ExampleTtsConfig extends TtsConfig {
}

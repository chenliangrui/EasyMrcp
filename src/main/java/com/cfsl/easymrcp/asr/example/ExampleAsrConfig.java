package com.cfsl.easymrcp.asr.example;

import com.cfsl.easymrcp.domain.AsrConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "example-asr")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:asr/example-asr.properties", "file:asr/example-asr.properties"}, ignoreResourceNotFound = true)
public class ExampleAsrConfig extends AsrConfig {
}

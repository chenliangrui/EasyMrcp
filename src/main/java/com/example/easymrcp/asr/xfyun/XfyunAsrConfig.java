package com.example.easymrcp.asr.xfyun;

import com.example.easymrcp.domain.AsrConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:asr/xfyunAsr.properties", "file:asr/xfyunAsr.properties"}, ignoreResourceNotFound = true)
public class XfyunAsrConfig extends AsrConfig {

}
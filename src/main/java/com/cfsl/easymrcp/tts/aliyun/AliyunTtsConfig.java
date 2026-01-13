package com.cfsl.easymrcp.tts.aliyun;

import com.cfsl.easymrcp.domain.TtsConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun-tts")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:tts/aliyun-tts.properties", "file:tts/aliyun-tts.properties"}, ignoreResourceNotFound = true)
public class AliyunTtsConfig extends TtsConfig {
    // 使用的模型名称
    public String mode;
    // 阿里云百炼 API Key
    public String APIKey;
}

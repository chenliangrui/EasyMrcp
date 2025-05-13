package com.cfsl.easymrcp.tts.tencentcloud;

import com.cfsl.easymrcp.domain.TtsConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "tencent-cloud-tts")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:tts/tencent-cloud-tts.properties", "file:tts/tencent-cloud-tts.properties"}, ignoreResourceNotFound = true)
public class TxCloudTtsConfig extends TtsConfig {
    private String appId;
    private String secretId;
    private String secretKey;
}

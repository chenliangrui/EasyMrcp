package com.cfsl.easymrcp.asr.tencentcloud;

import com.cfsl.easymrcp.domain.AsrConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "tencent-cloud")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:asr/tencent-cloud.properties", "file:asr/tencent-cloud.properties"}, ignoreResourceNotFound = true)
public class TxCloudConfig extends AsrConfig {
    private String appId;
    private String secretId;
    private String secretKey;
}
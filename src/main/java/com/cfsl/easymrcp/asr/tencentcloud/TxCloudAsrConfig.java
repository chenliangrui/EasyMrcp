package com.cfsl.easymrcp.asr.tencentcloud;

import com.cfsl.easymrcp.domain.AsrConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "tencent-cloud-asr")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:asr/tencent-cloud-asr.properties", "file:asr/tencent-cloud-asr.properties"}, ignoreResourceNotFound = true)
public class TxCloudAsrConfig extends AsrConfig {
    private String appId;
    private String secretId;
    private String secretKey;
}
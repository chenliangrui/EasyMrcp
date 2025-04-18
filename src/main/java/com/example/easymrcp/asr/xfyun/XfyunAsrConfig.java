package com.example.easymrcp.asr.xfyun;

import com.example.easymrcp.domain.AsrConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "xfyun-asr")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:asr/xfyun-asr.properties", "file:asr/xfyun-asr.properties"}, ignoreResourceNotFound = true)
public class XfyunAsrConfig extends AsrConfig {
    // 地址与鉴权信息
    public String hostUrl;
    // 均到控制台-语音合成页面获取
    public String APPID;
    public String APISecret;
    public String APIKey;
}
package com.cfsl.easymrcp.asr.aliyunfunasr;

import com.cfsl.easymrcp.domain.AsrConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun-funasr")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:asr/aliyun-funasr.properties", "file:asr/aliyun-funasr.properties"}, ignoreResourceNotFound = true)
/**
 * 阿里云 FunASR 一句话识别配置，映射 {@code aliyun-funasr.*} 配置项。
 */
public class AliyunFunasrConfig extends AsrConfig {
    /** 阿里云 DashScope API Key。 */
    private String apiKey;
    /** 阿里云实时识别 WebSocket 接入地址。 */
    private String websocketUrl;
    /** 可选的业务空间 ID。 */
    private String workspaceId;
    /** 识别模型名称。 */
    private String model;
    /** 音频格式，当前按 PCM 发送。 */
    private String format;
    /** 音频采样率。 */
    private Integer sampleRate;
    /** 服务端句尾静音时长，单位毫秒。 */
    private Integer maxSentenceSilence;
    /** 可选热词表 ID。 */
    private String vocabularyId;
    /** 语言提示，多个值时用逗号分隔。 */
    private String languageHints;
    /** 是否由服务端补全语义标点。 */
    private Boolean semanticPunctuationEnabled;
    /** 是否开启服务端心跳事件。 */
    private Boolean heartbeat;
}

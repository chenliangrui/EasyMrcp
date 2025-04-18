package com.example.easymrcp.asr.funasr;

import com.example.easymrcp.domain.AsrConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "funasr")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:asr/funasr.properties", "file:asr/funasr.properties"}, ignoreResourceNotFound = true)
public class FunasrConfig extends AsrConfig {
    private String strChunkSize;
    private int chunkInterval;
    private int sendChunkSize;
    private String srvIp;
    private String srvPort;
    private String mode;
    private String hotwords;
    private String fsthotwords;
}
package com.example.easymrcp.asr.funasr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@PropertySource(value = {"classpath:asr/funasr.properties", "file:asr/funasr.properties"}, ignoreResourceNotFound = true)
@ConfigurationProperties
public class FunasrConfig {
    private String strChunkSize;
    private int chunkInterval;
    private int sendChunkSize;
    private String srvIp;
    private String srvPort;
}
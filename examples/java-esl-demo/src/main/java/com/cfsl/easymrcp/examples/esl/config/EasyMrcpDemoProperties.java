package com.cfsl.easymrcp.examples.esl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "easy-mrcp")
public class EasyMrcpDemoProperties {
    private String host;
    private int port;
}

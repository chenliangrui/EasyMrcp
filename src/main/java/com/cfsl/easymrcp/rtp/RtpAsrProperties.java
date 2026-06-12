package com.cfsl.easymrcp.rtp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rtp.asr")
public class RtpAsrProperties {
    private int reorderWindowPackets = 2;
    private int maxConsecutiveLossFill = 3;
}

package com.example.easymrcp.common;

import com.example.easymrcp.asr.AsrHandler;
import com.example.easymrcp.rtp.FunAsrProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 根据配置决定加载某个asr
 */
@Component
public class AsrCreator {
    @Value("${mrcp.asrMode}")
    String asrMode;

    public AsrHandler returnAsrHandler() {
        if (asrMode.equals("funasr")) {
            return new FunAsrProcessor();
        }
        return null;
    }
}

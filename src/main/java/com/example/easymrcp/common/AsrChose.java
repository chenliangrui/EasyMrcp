package com.example.easymrcp.common;

import com.example.easymrcp.asr.AsrHandler;
import org.springframework.stereotype.Component;

/**
 * 根据配置决定加载某个asr
 */
@Component
public class AsrChose {

    public AsrHandler returnAsrHandler() {
        return null;
    }
}

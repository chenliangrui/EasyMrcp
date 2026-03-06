package com.cfsl.easymrcp.tcp;

import lombok.Data;

import java.util.UUID;
import java.util.function.Consumer;

@Data
public class MrcpEventWithCallback{
    // 预加载TtsEngine id
    private String id;
    // 是否预加载了数据
    private boolean pre;
    private String eventType;
    // 是否预加载(normal正常处理、pre预处理、playPre播放预处理的数据)
    private Consumer<String> consumer;

    public MrcpEventWithCallback(String event) {
        this.eventType = event;
        this.id = UUID.randomUUID().toString();
    }
}

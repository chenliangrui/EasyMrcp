package com.cfsl.easymrcp.tcp;

import lombok.Data;

/**
 * EasyMrcp事件数据模型
 * 统一的EasyMrcp客户端和服务端事件通知格式
 */
@Data
public class MrcpEvent {
    private String id;
    private String event;
    private String data;

    public MrcpEvent(String id, TcpEventType event, String data) {
        this.id = id;
        this.event = event.name();
        this.data = data;
    }
} 
package com.cfsl.easymrcp.tcp;

import lombok.Data;

/**
 * EasyMrcp事件数据模型
 * 统一的EasyMrcp客户端和服务端事件通知格式
 */
@Data
public class MrcpEvent {
    // 通话uuid
    private String id;
    // 事件id
    private String eventId;
    private String event;
    private String data;

    public MrcpEvent() {
    }

    public MrcpEvent(String id, String eventId, TcpEventType event, String data) {
        this.id = id;
        this.eventId = eventId;
        this.event = event.name();
        this.data = data;
    }
} 
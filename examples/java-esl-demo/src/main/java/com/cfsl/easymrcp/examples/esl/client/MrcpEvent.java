package com.cfsl.easymrcp.examples.esl.client;

import lombok.Data;

@Data
public class MrcpEvent {
    private String id;
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

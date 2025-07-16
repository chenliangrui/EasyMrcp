package com.cfsl.easymrcp.tcp;

/**
 * TCP事件数据模型
 * 统一的TCP客户端和服务端事件通知格式
 */
public class TcpEvent {
    private String id;
    private String event;
    private String data;

    public TcpEvent() {
    }
    
    public TcpEvent(String id, String event, String data) {
        this.id = id;
        this.event = event;
        this.data = data;
    }

    public TcpEvent(String id, TcpEventType event, String data) {
        this.id = id;
        this.event = event.name();
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
} 
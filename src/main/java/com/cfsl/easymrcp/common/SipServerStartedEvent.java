package com.cfsl.easymrcp.common;

import org.springframework.context.ApplicationEvent;

/**
 * SIP服务器启动事件
 * 当SIP服务器成功启动时发布此事件
 */
public class SipServerStartedEvent extends ApplicationEvent {
    
    public SipServerStartedEvent(Object source) {
        super(source);
    }
} 
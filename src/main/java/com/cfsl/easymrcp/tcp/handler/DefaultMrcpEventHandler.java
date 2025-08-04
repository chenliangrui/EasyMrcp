package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.MrcpEventHandler;
import com.cfsl.easymrcp.tcp.MrcpEvent;
import com.cfsl.easymrcp.tcp.TcpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的TCP命令处理器实现
 */
public class DefaultMrcpEventHandler implements MrcpEventHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMrcpEventHandler.class);
    
    @Override
    public TcpResponse handleEvent(MrcpEvent event, TcpClientNotifier tcpClientNotifier) {
        LOGGER.warn("收到未知事件: {}", event.getEvent());
        return null;
    }
} 
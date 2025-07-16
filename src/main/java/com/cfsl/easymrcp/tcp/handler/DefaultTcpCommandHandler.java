package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpCommandHandler;
import com.cfsl.easymrcp.tcp.TcpEvent;
import com.cfsl.easymrcp.tcp.TcpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的TCP命令处理器实现
 */
public class DefaultTcpCommandHandler implements TcpCommandHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTcpCommandHandler.class);
    
    @Override
    public TcpResponse handleEvent(TcpEvent event, TcpClientNotifier tcpClientNotifier) {
        LOGGER.warn("收到未知事件: {}", event.getEvent());
        return null;
    }
} 
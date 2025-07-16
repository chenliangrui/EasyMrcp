package com.cfsl.easymrcp.tcp;

/**
 * TCP命令处理器接口
 */
public interface TcpCommandHandler {
    
    /**
     * 处理TCP事件
     *
     * @param event TCP事件
     * @param tcpClientNotifier TCP客户端通知器
     * @return 响应消息
     */
    TcpResponse handleEvent(TcpEvent event, TcpClientNotifier tcpClientNotifier);
} 
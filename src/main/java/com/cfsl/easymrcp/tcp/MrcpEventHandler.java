package com.cfsl.easymrcp.tcp;

/**
 * EasyMrcp事件处理器接口
 */
public interface MrcpEventHandler {
    
    /**
     * 处理EasyMrcp事件
     *
     * @param event EasyMrcp事件
     * @param tcpClientNotifier EasyMrcp客户端通知器
     * @return 响应消息
     */
    TcpResponse handleEvent(MrcpEvent event, TcpClientNotifier tcpClientNotifier);
} 
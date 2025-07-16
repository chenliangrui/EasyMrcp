package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpEvent;
import com.cfsl.easymrcp.tcp.TcpCommandHandler;
import com.cfsl.easymrcp.tcp.TcpResponse;

/**
 * Echo命令处理器
 * 用于回显客户端发送的消息，主要用于测试
 */
public class EchoCommandHandler implements TcpCommandHandler {

    /**
     * 处理Echo命令
     * 
     * @param event 事件
     * @param tcpClientNotifier 客户端通知器
     * @return 回显响应
     */
    @Override
    public TcpResponse handleEvent(TcpEvent event, TcpClientNotifier tcpClientNotifier) {
        // 简单地返回客户端发送的数据
        return TcpResponse.success(event.getId(), "Echo: " + event.getData());
    }
} 
package com.cfsl.easymrcp.tcp;

/**
 * TCP命令处理器接口
 */
public interface TcpCommandHandler {
    
    /**
     * 处理TCP命令
     *
     * @param command           客户端发送的命令
     * @param tcpClientNotifier
     * @return 服务器响应
     */
    TcpResponse handleCommand(TcpCommand command, TcpClientNotifier tcpClientNotifier);
} 
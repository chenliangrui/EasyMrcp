package com.cfsl.easymrcp.tcp;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TCP客户端通知器
 * 用于通过TCP连接向客户端发送通知消息
 */
@Slf4j
@Component
public class TcpClientNotifier {
    private final NettyConnectionManager connectionManager;
    
    @Autowired
    public TcpClientNotifier(NettyConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    /**
     * 向客户端发送事件通知
     *
     * @param clientId  客户端ID
     * @param eventType 事件类型
     * @param data      事件数据
     */
    public void sendEvent(String clientId, TcpEventType eventType, String data) {
        if (clientId == null || clientId.isEmpty()) {
            log.warn("客户端ID为空，无法发送事件通知");
            return;
        }
        
        // 检查客户端是否存在
        if (!connectionManager.hasClient(clientId)) {
            log.warn("客户端不存在，无法发送事件通知: {}", clientId);
            return;
        }
        
        try {
            MrcpEvent event = new MrcpEvent(clientId, eventType, data);
            connectionManager.sendToClient(clientId, event);
        } catch (Exception e) {
            log.error("发送事件通知异常: {}", eventType, e);
        }
    }
} 
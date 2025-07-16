package com.cfsl.easymrcp.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TCP客户端通知器
 * 用于通过TCP连接向客户端发送通知消息
 */
@Component
public class TcpClientNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpClientNotifier.class);
    
    private final TcpConnectionManager connectionManager;
    
    @Autowired
    public TcpClientNotifier(TcpConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    /**
     * 向客户端发送事件通知
     * 
     * @param clientId 客户端ID
     * @param eventType 事件类型
     * @param data 事件数据
     * @return 是否发送成功
     */
    public boolean sendEvent(String clientId, TcpEventType eventType, String data) {
        if (clientId == null || clientId.isEmpty()) {
            LOGGER.warn("客户端ID为空，无法发送事件通知");
            return false;
        }
        
        // 检查客户端是否存在
        if (!connectionManager.hasClient(clientId)) {
            LOGGER.warn("客户端不存在，无法发送事件通知: {}", clientId);
            return false;
        }
        
        try {
            TcpEvent event = new TcpEvent(clientId, eventType, data);
            return connectionManager.sendToClient(clientId, event);
        } catch (Exception e) {
            LOGGER.error("发送事件通知异常: " + eventType, e);
            return false;
        }
    }
} 
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
     * 向客户端发送ASR结果通知
     * 
     * @param clientId 客户端ID
     * @param text 识别文本
     * @return 是否发送成功
     */
    public boolean sendAsrResultNotify(String clientId, String text) {
        if (clientId == null || clientId.isEmpty()) {
            LOGGER.warn("客户端ID为空，无法发送ASR结果通知");
            return false;
        }
        
        // 检查客户端是否存在
        if (!connectionManager.hasClient(clientId)) {
            LOGGER.warn("客户端不存在，无法发送ASR结果通知: {}", clientId);
            return false;
        }
        
        try {
            TcpResponse response = new TcpResponse();
            response.setId(clientId);
            response.setCode(200);
            response.setMessage("ASR结果");
            response.setData(text);
            
            return connectionManager.sendToClient(clientId, response);
        } catch (Exception e) {
            LOGGER.error("发送ASR结果通知异常", e);
            return false;
        }
    }
} 
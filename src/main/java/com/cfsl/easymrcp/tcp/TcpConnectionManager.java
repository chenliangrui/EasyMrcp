package com.cfsl.easymrcp.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP连接管理器
 * 负责管理所有TCP客户端连接，提供向客户端发送消息的功能
 */
@Component
public class TcpConnectionManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpConnectionManager.class);
    
    // 客户端连接映射表，key为客户端ID，value为客户端连接信息
    private final Map<String, ClientConnection> clientConnections = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper;
    
    @Autowired
    public TcpConnectionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 检查客户端连接是否存在
     *
     * @param clientId 客户端ID
     * @return 是否存在
     */
    public boolean hasClient(String clientId) {
        return clientConnections.containsKey(clientId);
    }
    
    /**
     * 注册新的客户端连接
     *
     * @param clientId 客户端ID
     * @param socket 客户端Socket连接
     * @param writer 客户端输出流（兼容旧版实现）
     */
    public void registerClient(String clientId, Socket socket, PrintWriter writer) {
        if (!clientConnections.containsKey(clientId)) {
            try {
                // 创建客户端连接对象
                ClientConnection connection = new ClientConnection(clientId, socket, writer, socket.getOutputStream());

                // 注册到映射表
                clientConnections.put(clientId, connection);

                LOGGER.info("客户端已注册, ID: {}, 地址: {}", clientId, socket.getInetAddress().getHostAddress());
            } catch (IOException e) {
                LOGGER.error("获取客户端输出流失败: {}", clientId, e);
            }
        }
    }
    
    /**
     * 向指定客户端发送数据
     *
     * @param clientId 客户端ID
     * @param data 要发送的数据
     * @return 是否发送成功
     */
    public boolean sendToClient(String clientId, Object data) {
        ClientConnection connection = clientConnections.get(clientId);
        if (connection == null) {
            LOGGER.warn("客户端不存在: {}", clientId);
            return false;
        }
        
        try {
            String jsonData = objectMapper.writeValueAsString(data);
            
            // 创建消息包
            TcpMessagePacket packet = new TcpMessagePacket(jsonData);
            
            // 打包并发送
            byte[] packedData = packet.pack();
            connection.outputStream.write(packedData);
            connection.outputStream.flush();
            
            LOGGER.info("成功发送数据到客户端 {}: {}", clientId, jsonData);
            return true;
        } catch (Exception e) {
            LOGGER.error("发送数据到客户端失败: {}", clientId, e);
            return false;
        }
    }
    
    /**
     * 注销客户端连接
     *
     * @param clientId 客户端ID
     */
    public void unregisterClient(String clientId) {
        ClientConnection connection = clientConnections.remove(clientId);
        if (connection != null) {
            try {
                connection.socket.close();
            } catch (IOException e) {
                LOGGER.error("关闭客户端Socket异常: {}", clientId, e);
            }
            LOGGER.info("客户端已注销: {}", clientId);
        }
    }
    
    /**
     * 客户端连接信息
     */
    private static class ClientConnection {
        private final String clientId;
        private final Socket socket;
        private final PrintWriter writer;  // 兼容旧版API
        private final OutputStream outputStream;  // 新增输出流用于直接写入字节数据
        
        public ClientConnection(String clientId, Socket socket, PrintWriter writer, OutputStream outputStream) {
            this.clientId = clientId;
            this.socket = socket;
            this.writer = writer;
            this.outputStream = outputStream;
        }
    }
} 
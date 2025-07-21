package com.cfsl.easymrcp.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty连接管理器
 * 负责管理TCP客户端连接和消息发送
 */
@Slf4j
@Component
public class NettyConnectionManager {
    
    // 客户端连接映射表，key为客户端ID，value为客户端Channel
    private final Map<String, Channel> clientChannels = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper;
    
    @Autowired
    public NettyConnectionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 检查客户端连接是否存在
     *
     * @param clientId 客户端ID
     * @return 是否存在
     */
    public boolean hasClient(String clientId) {
        return clientChannels.containsKey(clientId);
    }
    
    /**
     * 注册新的客户端连接
     *
     * @param clientId 客户端ID
     * @param channel 客户端Channel
     */
    public void registerClient(String clientId, Channel channel) {
        if (!clientChannels.containsKey(clientId)) {
            // 注册到映射表
            clientChannels.put(clientId, channel);
            
            // 为Channel添加关闭监听器，在连接关闭时自动移除
            channel.closeFuture().addListener((ChannelFuture future) -> {
                unregisterClient(clientId);
            });
            
            log.info("客户端已注册, ID: {}, 地址: {}", clientId, channel.remoteAddress());
        }
    }
    
    /**
     * 向指定客户端发送数据
     *
     * @param clientId 客户端ID
     * @param data 要发送的数据对象
     * @return 是否发送成功
     */
    public boolean sendToClient(String clientId, Object data) {
        Channel channel = clientChannels.get(clientId);
        if (channel == null) {
            log.warn("客户端不存在: {}", clientId);
            return false;
        }
        
        if (!channel.isActive()) {
            log.warn("客户端连接已断开: {}", clientId);
            unregisterClient(clientId);
            return false;
        }
        
        try {
            // 将对象转换为JSON字符串
            String jsonData = objectMapper.writeValueAsString(data);
            
            // 使用Netty的写入方法
            channel.writeAndFlush(jsonData);
            
            log.info("成功发送数据到客户端 {}: {}", clientId, jsonData);
            return true;
        } catch (Exception e) {
            log.error("发送数据到客户端失败: {}", clientId, e);
            return false;
        }
    }
    
    /**
     * 注销客户端连接
     *
     * @param clientId 客户端ID
     */
    public void unregisterClient(String clientId) {
        Channel channel = clientChannels.remove(clientId);
        if (channel != null) {
            log.info("客户端已注销: {}", clientId);
        }
    }
    
    /**
     * 根据Channel查找客户端ID
     *
     * @param channel Channel对象
     * @return 客户端ID，如果不存在则返回null
     */
    public String getClientIdByChannel(Channel channel) {
        for (Map.Entry<String, Channel> entry : clientChannels.entrySet()) {
            if (entry.getValue() == channel) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * 关闭所有连接
     */
    public void closeAll() {
        for (Channel channel : clientChannels.values()) {
            channel.close();
        }
        clientChannels.clear();
    }
} 
package com.cfsl.easymrcp.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * TCP客户端服务
 * 提供向TCP客户端发送数据的功能
 */
@Service
public class TcpClientService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpClientService.class);
    
    private final TcpConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public TcpClientService(TcpConnectionManager connectionManager, ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }
} 
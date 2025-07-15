package com.cfsl.easymrcp.tcp;

import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Socket;

/**
 * TCP服务器处理器工厂，负责创建处理客户端连接的处理器
 */
@Component
public class TcpServerHandlerFactory {

    private final ObjectMapper objectMapper;
    private final TcpConnectionManager connectionManager;
    private final MrcpManage mrcpManage;

    @Autowired
    public TcpServerHandlerFactory(ObjectMapper objectMapper, 
                                  TcpConnectionManager connectionManager, 
                                  MrcpManage mrcpManage) {
        this.objectMapper = objectMapper;
        this.connectionManager = connectionManager;
        this.mrcpManage = mrcpManage;
    }
    
    /**
     * 创建客户端连接处理器
     *
     * @param clientSocket 客户端Socket
     * @return 处理器实例
     */
    public Runnable createHandler(Socket clientSocket) {
        return new TcpServerHandler(clientSocket, objectMapper, connectionManager, mrcpManage);
    }
} 
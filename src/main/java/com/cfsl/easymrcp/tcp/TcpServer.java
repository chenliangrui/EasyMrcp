package com.cfsl.easymrcp.tcp;

import com.cfsl.easymrcp.common.EMConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP服务器
 */
@Component
@ConditionalOnProperty(name = EMConstant.TCP_SERVER_ENABLED, havingValue = "true")
public class TcpServer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpServer.class);
    
    @Value("${" + EMConstant.TCP_SERVER_PORT + ":" + EMConstant.DEFAULT_TCP_PORT + "}")
    private int port;
    
    private final TcpServerHandlerFactory handlerFactory;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean running = false;
    
    @Autowired
    public TcpServer(TcpServerHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
    }
    
    @PostConstruct
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            executorService = Executors.newCachedThreadPool();
            running = true;
            
            LOGGER.info("TCP服务器已启动，监听端口: {}", port);
            
            // 启动服务器监听线程
            new Thread(this::listen).start();
            
        } catch (IOException e) {
            LOGGER.error("启动TCP服务器失败", e);
        }
    }
    
    private void listen() {
        while (running) {
            try {
                // 等待客户端连接
                Socket clientSocket = serverSocket.accept();
                
                // 为每个客户端连接创建一个处理器
                Runnable handler = handlerFactory.createHandler(clientSocket);
                executorService.submit(handler);
                
            } catch (IOException e) {
                if (running) {
                    LOGGER.error("接受客户端连接异常", e);
                }
            }
        }
    }
    
    @PreDestroy
    public void stop() {
        running = false;
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.error("关闭TCP服务器异常", e);
            }
        }
        
        LOGGER.info("TCP服务器已停止");
    }
} 
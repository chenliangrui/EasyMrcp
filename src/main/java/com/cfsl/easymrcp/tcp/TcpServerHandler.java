package com.cfsl.easymrcp.tcp;

import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.handler.EchoCommandHandler;
import com.cfsl.easymrcp.tcp.handler.InterruptAndSpeakCommandHandler;
import com.cfsl.easymrcp.tcp.handler.SpeakCommandHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * TCP服务器客户端连接处理器
 */
public class TcpServerHandler implements Runnable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpServerHandler.class);
    
    private final Socket clientSocket;
    private final ObjectMapper objectMapper;
    private final TcpConnectionManager connectionManager;
    private final MrcpManage mrcpManage;
    
    public TcpServerHandler(Socket clientSocket, ObjectMapper objectMapper,
                           TcpConnectionManager connectionManager, MrcpManage mrcpManage) {
        this.clientSocket = clientSocket;
        this.objectMapper = objectMapper;
        this.connectionManager = connectionManager;
        this.mrcpManage = mrcpManage;
    }
    
    @Override
    public void run() {
        try {
            // 获取输入流和输出流
            InputStream inputStream = clientSocket.getInputStream();
            OutputStream outputStream = clientSocket.getOutputStream();
            
            // 创建消息读取器
            TcpMessageReader messageReader = new TcpMessageReader(inputStream);
            
            LOGGER.info("客户端已连接: {}", clientSocket.getInetAddress().getHostAddress());
            
            // 创建一个PrintWriter用于向客户端发送文本响应
            PrintWriter textWriter = new PrintWriter(outputStream, true);
            
            // 循环处理客户端消息
            while (!clientSocket.isClosed()) {
                // 读取完整的消息
                List<String> messages = messageReader.readMessages();
                
                // 处理每个消息
                for (String message : messages) {
                    try {
                        // 解析客户端命令
                        TcpCommand command = objectMapper.readValue(message, TcpCommand.class);
                        
                        // 检查客户端ID
                        if (command.getId() == null || command.getId().isEmpty()) {
                            // ID不能为空
                            sendResponse(outputStream, TcpResponse.error("unknown", "客户端ID不能为空"));
                        } else {
                            // 处理命令
                            processCommand(command, outputStream, textWriter);
                        }
                    } catch (Exception e) {
                        LOGGER.error("处理客户端消息错误", e);
                        sendResponse(outputStream, TcpResponse.error("unknown", "处理请求错误: " + e.getMessage()));
                    }
                }
                
                // 短暂休眠，避免CPU占用过高
                Thread.sleep(10);
            }
            
            LOGGER.info("客户端断开连接: {}", clientSocket.getInetAddress().getHostAddress());
        } catch (IOException | InterruptedException e) {
            LOGGER.error("客户端连接处理异常", e);
        }
    }
    
    /**
     * 处理客户端命令
     *
     * @param command 客户端命令
     * @param outputStream 输出流
     * @param textWriter 文本输出流，用于兼容旧版PrintWriter方式
     * @throws IOException IO异常
     */
    private void processCommand(TcpCommand command, OutputStream outputStream, PrintWriter textWriter) throws IOException {
        String clientId = command.getId();
        
        // 检查连接是否已注册
        boolean isExistingClient = connectionManager.hasClient(clientId);
        
        // 如果客户端不存在，则注册新连接
        if (!isExistingClient) {
            connectionManager.registerClient(clientId, clientSocket, textWriter);
            mrcpManage.updateConnection(clientId);
            LOGGER.info("注册新客户端连接: {}", clientId);
        }
        
        // 根据命令类型处理请求
        TcpResponse response;
        if (command.getCommand() != null && !command.getCommand().isEmpty()) {
            // 创建对应的命令处理器
            TcpCommandHandler handler = createCommandHandler(command.getCommand());
            // 执行命令处理
            response = handler.handleCommand(command);
        } else {
            // 简单响应
            response = TcpResponse.success(clientId, isExistingClient ? 
                "命令已处理" : "连接已注册");
        }
        
        // 发送响应
        sendResponse(outputStream, response);
    }
    
    /**
     * 发送响应到客户端
     * 
     * @param outputStream 输出流
     * @param response 响应对象
     * @throws IOException IO异常
     */
    private void sendResponse(OutputStream outputStream, TcpResponse response) throws IOException {
        try {
            // 将响应对象转换为JSON字符串
            String jsonResponse = objectMapper.writeValueAsString(response);
            
            // 创建消息包
            TcpMessagePacket packet = new TcpMessagePacket(jsonResponse);
            
            // 打包并发送
            byte[] data = packet.pack();
            outputStream.write(data);
            outputStream.flush();
            
            LOGGER.info("已发送响应: {}", jsonResponse);
        } catch (IOException e) {
            LOGGER.error("发送响应失败", e);
            throw e;
        }
    }
    
    /**
     * 根据命令类型创建对应的处理器
     *
     * @param commandType 命令类型
     * @return 命令处理器
     */
    private TcpCommandHandler createCommandHandler(String commandType) {
        // 根据命令类型创建相应的处理器
        switch (commandType.toLowerCase()) {
            case "echo":
                return new EchoCommandHandler(mrcpManage);
            case "speak":
                return new SpeakCommandHandler(mrcpManage);
            case "interruptandspeak":
                return new InterruptAndSpeakCommandHandler(mrcpManage);
            // 可以在这里添加更多命令类型的处理器
            default:
                return new DefaultTcpCommandHandler();
        }
    }
} 
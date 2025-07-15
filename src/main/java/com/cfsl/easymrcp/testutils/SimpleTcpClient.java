package com.cfsl.easymrcp.testutils;

import com.cfsl.easymrcp.tcp.TcpMessagePacket;
import com.cfsl.easymrcp.tcp.TcpMessageReader;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

/**
 * 简单TCP客户端示例
 */
public class SimpleTcpClient {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;
    
    private final String serverHost;
    private final int serverPort;
    private Socket socket;
    private OutputStream outputStream;
    private TcpMessageReader messageReader;
    private final ObjectMapper objectMapper;
    private boolean connected = false;
    private String clientId;
    
    public SimpleTcpClient(String serverHost, int serverPort, String clientId) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.objectMapper = new ObjectMapper();
        this.clientId = clientId != null ? clientId : UUID.randomUUID().toString();
    }
    
    /**
     * 连接到服务器
     * 
     * @return 是否连接成功
     */
    public boolean connect() {
        try {
            socket = new Socket(serverHost, serverPort);
            outputStream = socket.getOutputStream();
            messageReader = new TcpMessageReader(socket.getInputStream());
            connected = true;
            System.out.println("已连接到服务器 " + serverHost + ":" + serverPort);
            
            // 启动接收线程
            new Thread(this::receiveMessages).start();
            
            // 发送注册命令
            sendCommand("register", null);
            
            return true;
        } catch (Exception e) {
            System.err.println("连接服务器失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            System.err.println("关闭连接异常: " + e.getMessage());
        }
    }
    
    /**
     * 发送命令
     * 
     * @param command 命令类型
     * @param data 命令数据
     */
    public void sendCommand(String command, Object data) {
        if (!connected) {
            System.err.println("未连接到服务器");
            return;
        }
        
        try {
            Map<String, Object> commandMap = new HashMap<>();
            commandMap.put("id", clientId);
            commandMap.put("command", command);
            commandMap.put("data", data);
            
            String jsonCommand = objectMapper.writeValueAsString(commandMap);
            // 打包并发送
            TcpMessagePacket packet = new TcpMessagePacket(jsonCommand);
            byte[] packedData = packet.pack();
            outputStream.write(packedData);
            outputStream.flush();
            
            System.out.println("发送命令: " + command + ", 数据: " + data);
        } catch (Exception e) {
            System.err.println("发送命令异常: " + e.getMessage());
        }
    }
    
    /**
     * 接收服务器消息
     */
    private void receiveMessages() {
        try {
            while (connected) {
                // 读取完整消息
                List<String> messages = messageReader.readMessages();
                
                // 处理每个消息
                for (String message : messages) {
                    try {
                        // 格式化JSON输出
                        Object json = objectMapper.readValue(message, Object.class);
                        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                        System.out.println("收到响应: " + prettyJson);
                    } catch (Exception e) {
                        System.out.println("收到响应: " + message);
                    }
                }
                
                // 短暂休眠，避免CPU占用过高
                Thread.sleep(10);
            }
        } catch (Exception e) {
            if (connected) {
                System.err.println("接收消息异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 命令行交互
     */
    public void startCommandLine() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("TCP客户端已启动，客户端ID: " + clientId);
        System.out.println("输入命令，格式: <command> <data>");
        System.out.println("输入'exit'退出");
        
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            
            if ("exit".equalsIgnoreCase(input)) {
                break;
            }
            
            String[] parts = input.split("\\s+", 2);
            String command = parts[0];
            String data = parts.length > 1 ? parts[1] : null;
            
            sendCommand(command, data);
        }
        
        close();
        scanner.close();
    }
    
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : SERVER_HOST;
        int port = SERVER_PORT;
        String clientId = args.length > 2 ? args[2] : UUID.randomUUID().toString();
        
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("端口格式错误，使用默认端口: " + SERVER_PORT);
            }
        }
        
        SimpleTcpClient client = new SimpleTcpClient(host, port, clientId);
        if (client.connect()) {
            client.startCommandLine();
        }
    }
} 
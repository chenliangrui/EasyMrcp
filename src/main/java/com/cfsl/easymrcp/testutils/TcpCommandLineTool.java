package com.cfsl.easymrcp.testutils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TCP命令行工具，用于执行单个命令并获取响应
 */
public class TcpCommandLineTool {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;
    private static final int TIMEOUT_SECONDS = 5;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: TcpCommandLineTool <command> [data] [client_id] [host] [port]");
            System.out.println("  command: 命令类型 (如: echo, status, register)");
            System.out.println("  data: 命令数据 (可选)");
            System.out.println("  client_id: 客户端ID (可选，默认生成UUID)");
            System.out.println("  host: 服务器主机 (默认: " + SERVER_HOST + ")");
            System.out.println("  port: 服务器端口 (默认: " + SERVER_PORT + ")");
            return;
        }

        String command = args[0];
        String data = args.length > 1 ? args[1] : null;
        String clientId = args.length > 2 ? args[2] : UUID.randomUUID().toString();
        String host = args.length > 3 ? args[3] : SERVER_HOST;
        int port = SERVER_PORT;

        if (args.length > 4) {
            try {
                port = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                System.err.println("端口格式错误，使用默认端口: " + SERVER_PORT);
            }
        }

        try {
            // 创建连接
            Socket socket = new Socket(host, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // 创建JSON对象映射器
            ObjectMapper objectMapper = new ObjectMapper();
            
            // 创建命令
            Map<String, Object> commandMap = new HashMap<>();
            commandMap.put("id", clientId);
            commandMap.put("command", command);
            commandMap.put("data", data);
            
            // 发送命令
            String jsonCommand = objectMapper.writeValueAsString(commandMap);
            // 确保发送的是单行完整的JSON
            jsonCommand = jsonCommand.replace("\n", "").replace("\r", "");
            out.println(jsonCommand);
            
            System.out.println("使用客户端ID: " + clientId);
            System.out.println("发送命令: " + command + (data != null ? " 数据: " + data : ""));
            
            // 等待响应
            CountDownLatch latch = new CountDownLatch(1);
            
            // 启动接收线程
            new Thread(() -> {
                try {
                    String line = in.readLine();
                    if (line != null) {
                        // 格式化JSON输出
                        Object json = objectMapper.readValue(line, Object.class);
                        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
                    }
                } catch (Exception e) {
                    System.err.println("接收响应异常: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
            
            // 等待响应或超时
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("等待响应超时");
            }
            
            // 关闭连接
            socket.close();
            
        } catch (Exception e) {
            System.err.println("执行命令异常: " + e.getMessage());
        }
    }
} 
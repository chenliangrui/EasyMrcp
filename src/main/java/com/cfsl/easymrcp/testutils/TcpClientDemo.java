package com.cfsl.easymrcp.testutils;

import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.tcp.TcpEvent;
import com.cfsl.easymrcp.tcp.TcpEventType;
import com.cfsl.easymrcp.tcp.TcpMessagePacket;
import com.cfsl.easymrcp.tcp.TcpMessageReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Setter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

/**
 * TCP客户端Demo
 */
public class TcpClientDemo {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;
    
    private final String serverHost;
    private final int serverPort;
    private Socket socket;
    private OutputStream outputStream;
    private TcpMessageReader messageReader;
    private final ObjectMapper objectMapper;
    private boolean connected = false;
    @Setter
    private String clientId;
    
    public TcpClientDemo(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        // 默认生成一个客户端ID
        this.clientId = "55909e3c-1cae-4113-afd5-7c3587b26636";
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
            sendMessage("asr", null);
            
            return true;
        } catch (IOException e) {
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
        } catch (IOException e) {
            System.err.println("关闭连接异常: " + e.getMessage());
        }
    }
    
    /**
     * 发送消息
     * 
     * @param event 事件类型
     * @param data 消息数据
     */
    public void sendMessage(String event, String data) {
        if (!connected) {
            System.err.println("未连接到服务器");
            return;
        }
        
        try {
            TcpEvent message = new TcpEvent(clientId, event, data);
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            
            // 打包消息并发送
            TcpMessagePacket packet = new TcpMessagePacket(jsonMessage);
            byte[] packedData = packet.pack();
            outputStream.write(packedData);
            outputStream.flush();
            
            System.out.println("发送事件: " + event);
            if (data != null) {
                System.out.println("数据: " + data);
            }
        } catch (Exception e) {
            System.err.println("发送消息异常: " + e.getMessage());
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
                        // 解析JSON消息
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        String event = jsonObject.getString("event");
                        String data = jsonObject.getString("data");
                        
                        System.out.println("收到事件: " + event);
                        System.out.println("事件数据: " + data);
                        
                        // 根据事件类型处理
                        if (TcpEventType.RecognitionComplete.name().equals(event)) {
                            System.out.println("收到ASR识别结果，开始TTS");
                            sendMessage("speak", "清晨晾衣时，发现窗台缝隙里钻出一株紫茉莉，单瓣花朵像被露水揉皱的绢纸。");
                        } else if (TcpEventType.SpeakComplete.name().equals(event)) {
                            System.out.println("TTS播放完成");
                        } else if (TcpEventType.SpeakInterrupted.name().equals(event)) {
                            System.out.println("TTS被打断");
                        } else if (TcpEventType.InterruptAndSpeak.name().equals(event)) {
                            System.out.println("打断并开始新的TTS");
                        }
                    } catch (Exception e) {
                        System.out.println("收到消息: " + message);
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
     * 交互式命令行
     */
    private void startCommandLine() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("TCP客户端已启动，可用命令: ");
        System.out.println("1. speak <message> - 发送文本合成语音");
        System.out.println("2. interrupt <message> - 打断当前TTS并合成新语音");
        System.out.println("3. asr - 启动语音识别");
        System.out.println("4. id [newid] - 获取或设置客户端ID");
        System.out.println("5. exit - 退出");
        
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            
            if ("exit".equalsIgnoreCase(input)) {
                break;
            } else if (input.startsWith("speak ")) {
                String message = input.substring(6);
                sendMessage("speak", message);
            } else if (input.startsWith("interrupt ")) {
                String message = input.substring(10);
                sendMessage("interruptandspeak", message);
            } else if ("asr".equalsIgnoreCase(input)) {
                sendMessage("asr", null);
            } else if (input.startsWith("id")) {
                String[] parts = input.split("\\s+", 2);
                if (parts.length > 1) {
                    setClientId(parts[1]);
                    System.out.println("已设置客户端ID: " + clientId);
                    // 发送注册命令更新ID
                    sendMessage("register", null);
                } else {
                    System.out.println("当前客户端ID: " + clientId);
                }
            } else {
                System.out.println("未知命令: " + input);
            }
        }
        
        close();
        scanner.close();
    }
    
    public static void main(String[] args) {
        String host = SERVER_HOST;
        int port = SERVER_PORT;
        
        // 解析命令行参数
        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("端口格式错误，使用默认端口: " + SERVER_PORT);
            }
        }
        
        TcpClientDemo client = new TcpClientDemo(host, port);
        if (client.connect()) {
            client.startCommandLine();
        }
    }
} 
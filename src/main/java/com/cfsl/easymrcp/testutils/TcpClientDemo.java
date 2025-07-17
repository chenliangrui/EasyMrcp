package com.cfsl.easymrcp.testutils;

import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.asr.ASRConstant;
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
 * 用于演示与服务器进行基于TcpEvent的通信
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
        this.clientId = "0b4099c2-0db6-4087-b1e2-9f43cbb21a1b";
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
            
            // 发送注册事件
            sendEvent(TcpEventType.ClientConnect, null);
            
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
     * 发送自定义事件
     * 
     * @param eventType 事件类型
     * @param data 事件数据
     */
    public void sendEvent(String eventType, Object data) {
        if (!connected) {
            System.err.println("未连接到服务器");
            return;
        }
        
        try {
            // 创建TcpEvent对象
            TcpEvent event = new TcpEvent();
            event.setId(clientId);
            event.setEvent(eventType);
            event.setData(data != null ? data.toString() : null);
            
            // 转换为JSON
            String jsonEvent = objectMapper.writeValueAsString(event);
            
            // 打包消息并发送
            TcpMessagePacket packet = new TcpMessagePacket(jsonEvent);
            byte[] packedData = packet.pack();
            outputStream.write(packedData);
            outputStream.flush();
            
            System.out.println("发送事件: " + jsonEvent);
        } catch (Exception e) {
            System.err.println("发送事件异常: " + e.getMessage());
        }
    }
    
    /**
     * 发送标准事件
     * 
     * @param eventType 标准事件类型
     * @param data 事件数据
     */
    public void sendEvent(TcpEventType eventType, String data) {
        if (!connected) {
            System.err.println("未连接到服务器");
            return;
        }
        
        try {
            // 创建TcpEvent对象
            TcpEvent event = new TcpEvent(clientId, eventType, data);
            
            // 转换为JSON
            String jsonEvent = objectMapper.writeValueAsString(event);
            
            // 打包消息并发送
            TcpMessagePacket packet = new TcpMessagePacket(jsonEvent);
            byte[] packedData = packet.pack();
            outputStream.write(packedData);
            outputStream.flush();
            
            System.out.println("发送事件: " + jsonEvent);
        } catch (Exception e) {
            System.err.println("发送事件异常: " + e.getMessage());
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
                        System.out.println("收到响应: " + message);
                        
                        // 解析JSON
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        String eventName = jsonObject.getString("event");
                        String data = jsonObject.getString("data");
                        
                        // 处理标准事件类型
                        handleStandardEvent(eventName, data);
                        
                    } catch (Exception e) {
                        System.out.println("处理响应异常: " + e.getMessage());
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
     * 处理标准事件类型
     * 
     * @param eventName 事件名称
     * @param data 事件数据
     */
    private void handleStandardEvent(String eventName, String data) {
        // 尝试将事件名称转换为标准事件类型
        try {
            TcpEventType eventType = TcpEventType.valueOf(eventName);
            
            // 根据标准事件类型处理
            switch (eventType) {
                case RecognitionComplete:
                    System.out.println("语音识别完成: " + data);
                    sendEvent(TcpEventType.Speak, data);
                    break;
                    
                case NoInputTimeout:
                    System.out.println("语音识别超时: 未检测到输入");
                    sendEvent(TcpEventType.Speak, "您好，您还在线吗?");
                    break;
                    
                case SpeakComplete:
                    System.out.println("语音合成播放完成");
                    break;
                    
                case SpeakInterrupted:
                    System.out.println("语音合成被打断");
                    break;
                    
                case InterruptAndSpeak:
                    System.out.println("打断当前TTS并开始新的TTS: " + data);
                    break;
                    
                default:
                    System.out.println("收到标准事件: " + eventType + ", 数据: " + data);
            }
        } catch (IllegalArgumentException e) {
            // 非标准事件类型，按自定义事件处理
            handleCustomEvent(eventName, data);
        }
    }
    
    /**
     * 处理自定义事件
     * 
     * @param eventName 事件名称
     * @param data 事件数据
     */
    private void handleCustomEvent(String eventName, String data) {
        if ("AsrResult".equals(eventName)) {
            System.out.println("收到语音识别结果: " + data);
            // 示例：在识别到结果后自动发送TTS请求
            sendEvent("speak", "您好，已收到您的语音识别结果");
        } else {
            System.out.println("收到自定义事件: " + eventName + ", 数据: " + data);
        }
    }
    
    /**
     * 交互式命令行
     */
    private void startCommandLine() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("TCP客户端已启动，可用事件: ");
        System.out.println("1. echo <message> - 发送Echo事件");
        System.out.println("2. status - 获取服务器状态");
        System.out.println("3. id [newid] - 获取或设置客户端ID");
        System.out.println("4. speak <message> - 发送语音合成事件");
        System.out.println("5. detect-speech - 开始语音识别");
        System.out.println("6. interrupt - 打断当前TTS");
        System.out.println("7. interrupt_speak <message> - 打断当前TTS并播放新内容");
        System.out.println("8. exit - 退出");
        
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            
            if ("exit".equalsIgnoreCase(input)) {
                break;
            } else if (input.startsWith("echo ")) {
                String message = input.substring(5);
                sendEvent("echo", message);
            } else if ("status".equalsIgnoreCase(input)) {
                sendEvent("status", null);
            } else if (input.startsWith("speak ")) {
                String message = input.substring(6);
                sendEvent(TcpEventType.Speak, message);
            } else if ("detect-speech".equalsIgnoreCase(input)) {
                JSONObject detectSpeechParams = new JSONObject();
                detectSpeechParams.put(ASRConstant.StartInputTimers, false);
                detectSpeechParams.put(ASRConstant.NoInputTimeout, 15000);
                detectSpeechParams.put(ASRConstant.SpeechCompleteTimeout, 800);
                sendEvent(TcpEventType.DetectSpeech, detectSpeechParams.toJSONString());
            } else if ("interrupt".equalsIgnoreCase(input)) {
                sendEvent(TcpEventType.SpeakInterrupted, null);
            } else if (input.startsWith("interrupt_speak ")) {
                String message = input.substring(15);
                sendEvent(TcpEventType.InterruptAndSpeak, message);
            } else if (input.startsWith("id")) {
                String[] parts = input.split("\\s+", 2);
                if (parts.length > 1) {
                    setClientId(parts[1]);
                    System.out.println("已设置客户端ID: " + clientId);
                    // 发送注册事件更新ID
                    sendEvent("register", null);
                } else {
                    System.out.println("当前客户端ID: " + clientId);
                }
            } else {
                System.out.println("未知事件: " + input);
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
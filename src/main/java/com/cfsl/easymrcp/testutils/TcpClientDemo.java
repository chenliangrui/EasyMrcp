package com.cfsl.easymrcp.testutils;

import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.tcp.TcpMessagePacket;
import com.cfsl.easymrcp.tcp.TcpMessageReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Setter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        this.clientId = "f659db22-5c82-4f6c-aebb-3bba6f183411";
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
            sendCommand("asr", null);
            
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
            
            // 打包消息并发送
            TcpMessagePacket packet = new TcpMessagePacket(jsonCommand);
            byte[] packedData = packet.pack();
            outputStream.write(packedData);
            outputStream.flush();
            
            System.out.println("发送命令: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(commandMap));
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
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        String text = jsonObject.getString("data");
                        System.out.println("收到响应: " + message);
                        if (jsonObject.getString("message").equals("AsrResult")) {
                            System.out.println("开始tts");
                            sendCommand("speak", "清晨晾衣时，发现窗台缝隙里钻出一株紫茉莉，单瓣花朵像被露水揉皱的绢纸。想起昨夜暴雨敲打空调外机的声响，恍惚间竟觉得是某种倔强的生长韵律。" +
                                    "午后路过旧书摊，牛皮纸箱里斜插着一本泛黄的《城南旧事》。翻开扉页看见铅笔写的“1979年春，赠小芳”，墨迹被岁月蛀出细小的虫洞。摊主趴在藤椅上打盹，蝉声与鼾声在樟树影子里此起彼伏，硬币搁在玻璃柜上的脆响惊飞了栖在招牌上的白腰雨燕。");
                        }
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
     * 交互式命令行
     */
    private void startCommandLine() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("TCP客户端已启动，可用命令: ");
        System.out.println("1. echo <message> - 发送Echo命令");
        System.out.println("2. status - 获取服务器状态");
        System.out.println("3. id [newid] - 获取或设置客户端ID");
        System.out.println("4. speak <message> - 发送speak命令");
        System.out.println("5. exit - 退出");
        
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            
            if ("exit".equalsIgnoreCase(input)) {
                break;
            } else if (input.startsWith("echo ")) {
                String message = input.substring(5);
                sendCommand("echo", message);
            } else if ("status".equalsIgnoreCase(input)) {
                sendCommand("status", null);
            }else if ("test".equalsIgnoreCase(input)) {
                String message = input.substring(4);
                sendCommand("test", message);
            }else if (input.startsWith("speak ")) {
                String message = input.substring(6);
                sendCommand("speak", message);
            } else if (input.startsWith("id")) {
                String[] parts = input.split("\\s+", 2);
                if (parts.length > 1) {
                    setClientId(parts[1]);
                    System.out.println("已设置客户端ID: " + clientId);
                    // 发送注册命令更新ID
                    sendCommand("register", null);
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
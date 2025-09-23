package com.cfsl.easymrcp.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.tcp.MrcpEvent;
import com.cfsl.easymrcp.tcp.TcpEventType;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Netty TCP客户端Demo
 * 用于演示与服务器进行基于MrcpEvent的通信
 */
@Slf4j
public class NettyTcpClient {
    
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;
    
    // 魔数，用于标识消息的开始，固定为 0x66AABB99（与服务端一致）
    private static final int MAGIC_NUMBER = 0x66AABB99;
    
    private final String serverHost;
    private final int serverPort;
    private EventLoopGroup group;
    private Channel channel;
    private boolean connected = false;
    @Setter
    private String clientId;
    
    private final CountDownLatch connectLatch = new CountDownLatch(1);
    
    public NettyTcpClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        // 通话建立后将pbx的通话uuid作为EasyMrcp本轮通话的唯一ID
        this.clientId = "63af808f-ce0e-47a3-9c8a-43880b88840a";
    }
    
    /**
     * 连接到服务器
     * 
     * @return 是否连接成功
     */
    public boolean connect() {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 添加解码器和编码器
                        pipeline.addLast(new MessagePacketDecoder());
                        pipeline.addLast(new MessagePacketEncoder());
                        // 添加业务处理器
                        pipeline.addLast(new ClientHandler());
                    }
                });
            
            // 连接服务器
            ChannelFuture future = bootstrap.connect(serverHost, serverPort).sync();
            channel = future.channel();
            
            // 等待连接完成
            boolean connected = connectLatch.await(5, TimeUnit.SECONDS);
            
            if (connected) {
                System.out.println("已连接到服务器 " + serverHost + ":" + serverPort);
                
                // 发送注册事件
                sendEvent(TcpEventType.ClientConnect, null);
                return true;
            } else {
                System.err.println("连接超时");
                close();
                return false;
            }
            
        } catch (InterruptedException e) {
            System.err.println("连接服务器失败: " + e.getMessage());
            close();
            return false;
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        connected = false;
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
            MrcpEvent event = new MrcpEvent(clientId, null,eventType, data);
            
            // 转换为JSON
            String jsonEvent = JSON.toJSONString(event);
            System.out.println("发送事件: " + jsonEvent);
            
            // 发送消息
            channel.writeAndFlush(jsonEvent);
        } catch (Exception e) {
            System.err.println("发送事件异常: " + e.getMessage());
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
            System.out.println("收到未知标准事件: " + eventName + ", 数据: " + data);
        }
    }
    
    /**
     * 交互式命令行
     */
    public void startCommandLine() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("Netty TCP客户端已启动，可用事件: ");
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
            } else if (input.startsWith("speak ")) {
                String message = input.substring(6);
                sendEvent(TcpEventType.Speak, message);
            } else if ("detect-speech".equalsIgnoreCase(input)) {
                JSONObject detectSpeechParams = new JSONObject();
                detectSpeechParams.put(ASRConstant.StartInputTimers, true);
                detectSpeechParams.put(ASRConstant.NoInputTimeout, 60000);
                detectSpeechParams.put(ASRConstant.SpeechCompleteTimeout, 800);
                sendEvent(TcpEventType.DetectSpeech, detectSpeechParams.toJSONString());
            } else if ("interrupt".equalsIgnoreCase(input)) {
                sendEvent(TcpEventType.SpeakInterrupted, null);
            } else if (input.startsWith("interrupt_speak ")) {
                String message = input.substring(15);
                sendEvent(TcpEventType.InterruptAndSpeak, message);
            } else if (input.equalsIgnoreCase("disconnect")) {
                sendEvent(TcpEventType.ClientDisConnect, null);
                // 短暂等待，确保消息发出
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                break;
            } else {
                System.out.println("未知事件: " + input);
            }
        }
        
        close();
        scanner.close();
    }
    
    /**
     * 客户端处理器
     */
    private class ClientHandler extends SimpleChannelInboundHandler<String> {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            connected = true;
            connectLatch.countDown();
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            connected = false;
            System.out.println("与服务器的连接已断开");
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String message) {
            System.out.println("收到响应: " + message);
            
            try {
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
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("客户端异常: " + cause.getMessage());
            ctx.close();
        }
    }
    
    /**
     * 消息解码器
     */
    private static class MessagePacketDecoder extends ByteToMessageDecoder {
        
        private static final int HEADER_LENGTH = 8;
        
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            // 标记当前读取位置
            in.markReaderIndex();
            
            // 如果数据不足以解析头部，则等待更多数据
            if (in.readableBytes() < HEADER_LENGTH) {
                return;
            }
            
            // 读取魔数
            int magic = in.readInt();
            
            // 验证魔数
            if (magic != MAGIC_NUMBER) {
                in.resetReaderIndex();
                in.skipBytes(1);
                return;
            }
            
            // 读取消息体长度
            int bodyLength = in.readInt();
            
            // 长度校验
            if (bodyLength < 0 || bodyLength > 10 * 1024 * 1024) {
                ctx.close();
                return;
            }
            
            // 如果数据不足以解析消息体，则重置读取位置，等待更多数据
            if (in.readableBytes() < bodyLength) {
                in.resetReaderIndex();
                return;
            }
            
            // 读取消息体
            byte[] bodyBytes = new byte[bodyLength];
            in.readBytes(bodyBytes);
            
            // 解析为字符串
            String messageContent = new String(bodyBytes, StandardCharsets.UTF_8);
            
            // 输出解析后的消息
            out.add(messageContent);
        }
    }
    
    /**
     * 消息编码器
     */
    private static class MessagePacketEncoder extends MessageToByteEncoder<String> {
        
        @Override
        protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) {
            byte[] bodyBytes = msg.getBytes(StandardCharsets.UTF_8);
            int bodyLength = bodyBytes.length;
            
            // 写入魔数
            out.writeInt(MAGIC_NUMBER);
            
            // 写入消息体长度
            out.writeInt(bodyLength);
            
            // 写入消息体
            out.writeBytes(bodyBytes);
        }
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
        
        NettyTcpClient client = new NettyTcpClient(host, port);
        if (client.connect()) {
            client.startCommandLine();
        }
    }
} 
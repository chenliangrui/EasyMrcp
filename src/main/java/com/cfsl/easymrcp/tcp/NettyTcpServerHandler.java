package com.cfsl.easymrcp.tcp;

import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.handler.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Netty TCP服务器处理器
 * 负责处理客户端连接和消息
 */
@Slf4j
public class NettyTcpServerHandler extends ChannelInboundHandlerAdapter {
    
    // 用于存储客户端ID的Attribute Key
    private static final AttributeKey<String> CLIENT_ID_KEY = AttributeKey.valueOf("clientId");
    
    private final ObjectMapper objectMapper;
    private final NettyConnectionManager connectionManager;
    private final MrcpManage mrcpManage;
    private final TcpClientNotifier tcpClientNotifier;
    
    public NettyTcpServerHandler(ObjectMapper objectMapper,
                                NettyConnectionManager connectionManager,
                                MrcpManage mrcpManage,
                                TcpClientNotifier tcpClientNotifier) {
        this.objectMapper = objectMapper;
        this.connectionManager = connectionManager;
        this.mrcpManage = mrcpManage;
        this.tcpClientNotifier = tcpClientNotifier;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
        log.info("客户端已连接: {}", address.getHostString());
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
        
        // 获取客户端ID
        String clientId = channel.attr(CLIENT_ID_KEY).get();
        
        if (clientId != null) {
            // 通知MRCP管理器移除相关资源
//            mrcpManage.removeMrcpCallData(clientId);
            
            // 从连接管理器中注销客户端
            connectionManager.unregisterClient(clientId);
            
            log.info("客户端已断开连接: {} (ID: {})", address.getHostString(), clientId);
        } else {
            log.info("未注册客户端断开连接: {}", address.getHostString());
        }
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 消息已经被解码为字符串
        String messageContent = (String) msg;
        Channel channel = ctx.channel();
        
        log.info("收到客户端消息: {}", messageContent);
        
        try {
            // 解析客户端事件
            MrcpEvent event = objectMapper.readValue(messageContent, MrcpEvent.class);
            
            // 检查客户端ID
            if (event.getId() == null || event.getId().isEmpty()) {
                // ID不能为空
                sendResponse(ctx, TcpResponse.error("unknown", "客户端ID不能为空"));
                return;
            }
            
            // 保存客户端ID到Channel属性
            channel.attr(CLIENT_ID_KEY).set(event.getId());
            
            // 处理事件
            processEvent(ctx, event);
            
        } catch (Exception e) {
            log.error("处理客户端消息错误", e);
            sendResponse(ctx, TcpResponse.error("unknown", "处理请求错误: " + e.getMessage()));
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // 处理空闲事件
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            
            // 读空闲或写空闲
            if (event.state() == IdleState.READER_IDLE || event.state() == IdleState.WRITER_IDLE) {
                // 获取客户端ID
                String clientId = ctx.channel().attr(CLIENT_ID_KEY).get();
                
                if (clientId != null) {
                    log.info("客户端 {} 空闲超时，关闭连接", clientId);
                    
                    // 移除相关资源
//                    mrcpManage.removeMrcpCallData(clientId);
                    connectionManager.unregisterClient(clientId);
                }
                
                // 关闭连接
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端连接异常", cause);
        ctx.close(); // 关闭连接
    }
    
    /**
     * 处理客户端事件
     *
     * @param ctx   Channel上下文
     * @param event 客户端事件
     * @throws IOException IO异常
     */
    private void processEvent(ChannelHandlerContext ctx, MrcpEvent event) throws IOException {
        String clientId = event.getId();
        Channel channel = ctx.channel();
        
        // 检查连接是否已注册
        boolean isExistingClient = connectionManager.hasClient(clientId);
        
        // 如果客户端不存在，则注册新连接
        if (!isExistingClient) {
            connectionManager.registerClient(clientId, channel);
            log.info("注册新客户端连接: {}", clientId);
        }
        
        // 根据事件类型处理请求
        TcpResponse response;
        if (event.getEvent() != null && !event.getEvent().isEmpty()) {
            // 创建对应的命令处理器
            MrcpEventHandler handler = createEventHandler(event.getEvent());
            // 执行事件处理
            response = handler.handleEvent(event, tcpClientNotifier);
        } else {
            // 简单响应
            response = TcpResponse.success(clientId, isExistingClient ?
                    "事件已处理" : "连接已注册");
        }
        
        // 发送响应
        sendResponse(ctx, response);
    }
    
    /**
     * 发送响应到客户端
     *
     * @param ctx      Channel上下文
     * @param response 响应对象
     * @throws IOException IO异常
     */
    private void sendResponse(ChannelHandlerContext ctx, TcpResponse response) throws IOException {
        try {
            // 将响应对象转换为JSON字符串
            String jsonResponse = objectMapper.writeValueAsString(response);
            
            // 直接发送字符串，编码器会处理格式化
            ctx.writeAndFlush(jsonResponse);
            
            log.info("已发送响应: {}", jsonResponse);
        } catch (Exception e) {
            log.error("发送响应失败", e);
            throw new IOException(e);
        }
    }
    
    /**
     * 根据事件类型创建对应的处理器
     *
     * @param eventType 事件类型
     * @return 命令处理器
     */
    private MrcpEventHandler createEventHandler(String eventType) {
        try {
            // 尝试将字符串转换为枚举值（区分大小写）
            TcpEventType enumEventType = TcpEventType.valueOf(eventType);
            
            // 使用枚举值进行比较
            switch (enumEventType) {
                case DetectSpeech:
                    return new DetectSpeechEventHandler(mrcpManage);
                case Speak:
                case InterruptAndSpeak:
                case Silence:
                    return new SpeakEventHandler(mrcpManage);
                case Interrupt:
                    return new InterruptEventHandler(mrcpManage);
                case ClientConnect:
                    return new ClientConnectEventHandler(mrcpManage);
                case ClientDisConnect:
                    return new ClientDisConnectEventHandler(mrcpManage);
                default:
                    return new DefaultMrcpEventHandler();
            }
        } catch (IllegalArgumentException e) {
            // 对于不是枚举值的字符串，使用传统方式处理
            switch (eventType.toLowerCase()) {
                case "echo":
                    return new EchoCommandHandler(mrcpManage);
                default:
                    return new DefaultMrcpEventHandler();
            }
        }
    }
} 
package com.cfsl.easymrcp.tcp;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * 基于Netty实现的TCP服务器
 */
@Slf4j
@Component
public class NettyTcpServer {
    
    @Value("${" + EMConstant.TCP_SERVER_PORT + ":" + EMConstant.DEFAULT_TCP_PORT + "}")
    private int port;
    
    private final ObjectMapper objectMapper;
    private final NettyConnectionManager connectionManager;
    private final MrcpManage mrcpManage;
    private final TcpClientNotifier tcpClientNotifier;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    @Autowired
    public NettyTcpServer(ObjectMapper objectMapper, 
                         NettyConnectionManager connectionManager, 
                         MrcpManage mrcpManage,
                         TcpClientNotifier tcpClientNotifier) {
        this.objectMapper = objectMapper;
        this.connectionManager = connectionManager;
        this.mrcpManage = mrcpManage;
        this.tcpClientNotifier = tcpClientNotifier;
    }
    
    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(1); // 接受连接的线程组
        workerGroup = new NioEventLoopGroup(); // 处理IO的线程组
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 添加自定义消息编解码器
                        pipeline.addLast(new NettyMessageDecoder());
                        pipeline.addLast(new NettyMessageEncoder());
                        // 添加业务处理器
                        pipeline.addLast(new NettyTcpServerHandler(objectMapper, connectionManager, mrcpManage, tcpClientNotifier));
                    }
                });
            
            // 绑定端口，开始接收连接
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            log.info("Netty TCP服务器已启动，监听端口: {}", port);
        } catch (Exception e) {
            log.error("启动Netty TCP服务器失败", e);
            shutdown();
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null && workerGroup != null) {
            // 优雅关闭，等待处理中的请求完成
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        // 关闭所有客户端连接
        connectionManager.closeAll();
        log.info("Netty TCP服务器已停止");
    }
} 
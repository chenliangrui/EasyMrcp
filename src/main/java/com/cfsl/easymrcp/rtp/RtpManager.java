package com.cfsl.easymrcp.rtp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RTP资源管理器
 * 负责管理RTP端口分配和Netty资源
 */
@Slf4j
@Component
public class RtpManager {
    @Value("${rtp.nettyThreads:0}")
    private int nettyThreads;

    // 共享的EventLoopGroup
    @Getter
    private EventLoopGroup rtpEventLoopGroup;

    // TODO 没有remove 已绑定的RTP通道
    private final Map<String, Channel> rtpChannels = new ConcurrentHashMap<>();

    /**
     * 初始化RTP管理器
     */
    @PostConstruct
    public void init() {
        // 创建共享的EventLoopGroup
        if (nettyThreads <= 0) {
            // 如果未设置或设置为0，则使用默认值（CPU核心数的2倍）
            rtpEventLoopGroup = new NioEventLoopGroup();
            log.info("使用默认线程数（CPU核心数的2倍）初始化Netty EventLoopGroup");
        } else {
            rtpEventLoopGroup = new NioEventLoopGroup(nettyThreads);
            log.info("使用配置的线程数 {} 初始化Netty EventLoopGroup", nettyThreads);
        }
    }

    public Channel createRtpChannel(String dialogId, int localPort, NettyAsrRtpProcessor nettyAsrRtpProcessor) {
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(rtpEventLoopGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
                    .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
                    .handler(nettyAsrRtpProcessor);
            Channel channel = bootstrap.bind(new InetSocketAddress(localPort)).sync().channel();
            rtpChannels.put(dialogId, channel);
            return channel;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 销毁RTP管理器，释放资源
     */
    @PreDestroy
    public void destroy() {
        // 关闭所有RTP通道
        rtpChannels.values().forEach(Channel::close);
        rtpChannels.clear();

        // 关闭EventLoopGroup
        if (rtpEventLoopGroup != null) {
            rtpEventLoopGroup.shutdownGracefully();
            log.info("RTP管理器已关闭");
        }
    }

    public void close(String dialogId) {
        rtpChannels.get(dialogId).close();
    }
} 
package com.cfsl.easymrcp.examples.esl.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Slf4j
public class EnhancedNettyTcpClient implements AutoCloseable {

    private static final int MAGIC_NUMBER = 0x66AABB99;

    private final String serverHost;
    private final int serverPort;
    private final EventLoopGroup group;
    private final boolean ownsGroup;
    private final Map<TcpEventType, BiConsumer<String, String>> eventCallbacks = new ConcurrentHashMap<>();

    private Channel channel;
    private volatile boolean connected;

    @Setter
    @Getter
    private String clientId;

    public EnhancedNettyTcpClient(String serverHost, int serverPort, String clientId, EventLoopGroup eventLoopGroup) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.clientId = clientId;
        this.ownsGroup = eventLoopGroup == null;
        this.group = eventLoopGroup == null ? new NioEventLoopGroup() : eventLoopGroup;
    }

    public void registerEventCallback(TcpEventType eventType, BiConsumer<String, String> callback) {
        eventCallbacks.put(eventType, callback);
    }

    public void connect(JSONObject connectParams) {
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new MessagePacketDecoder());
                        pipeline.addLast(new MessagePacketEncoder());
                        pipeline.addLast(new ClientHandler());
                    }
                });

        ChannelFuture future = bootstrap.connect(serverHost, serverPort);
        future.addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                channel = channelFuture.channel();
                connected = true;
                log.info("已连接到EasyMrcp服务器 {}:{}", serverHost, serverPort);
                sendEvent(TcpEventType.ClientConnect, connectParams == null ? null : connectParams.toJSONString());
            } else {
                log.error("连接EasyMrcp服务器失败", channelFuture.cause());
                handleConnectFailure(channelFuture.channel());
            }
        });
    }

    public void sendEvent(TcpEventType eventType, String data) {
        sendEvent(null, eventType, data);
    }

    public void sendEvent(String eventId, TcpEventType eventType, String data) {
        if (!connected || channel == null) {
            log.warn("未连接到EasyMrcp服务器，无法发送事件: {}", eventType);
            return;
        }

        MrcpEvent event = new MrcpEvent(clientId, eventId, eventType, data);
        String jsonEvent = JSON.toJSONString(event);
        log.info("发送EasyMrcp事件: {}", jsonEvent);
        channel.writeAndFlush(jsonEvent);
    }

    public void disconnect() {
        Channel currentChannel = channel;
        if (connected && currentChannel != null) {
            MrcpEvent event = new MrcpEvent(clientId, null, TcpEventType.ClientDisConnect, null);
            String jsonEvent = JSON.toJSONString(event);
            log.info("发送EasyMrcp事件: {}", jsonEvent);
            currentChannel.writeAndFlush(jsonEvent).awaitUninterruptibly();
        }
        connected = false;
        if (currentChannel != null) {
            currentChannel.close().awaitUninterruptibly(1000);
            channel = null;
        }
        if (ownsGroup && group != null) {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).awaitUninterruptibly();
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    private void handleConnectFailure(Channel failedChannel) {
        connected = false;
        if (channel == failedChannel) {
            channel = null;
        }
        if (failedChannel != null && failedChannel.isOpen()) {
            failedChannel.close();
        }
        if (ownsGroup && group != null) {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    private void handleStandardEvent(String eventId, String eventName, String data) {
        try {
            TcpEventType eventType = TcpEventType.valueOf(eventName);
            BiConsumer<String, String> callback = eventCallbacks.get(eventType);
            if (callback != null) {
                callback.accept(eventId, data);
            } else {
                log.info("收到事件但未注册回调: {}, data={}", eventType, data);
            }
        } catch (IllegalArgumentException e) {
            log.warn("收到未知事件: {}, data={}", eventName, data);
        }
    }

    private class ClientHandler extends SimpleChannelInboundHandler<String> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            connected = true;
            log.info("EasyMrcp TCP连接已建立");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            connected = false;
            log.info("EasyMrcp TCP连接已断开");
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String message) {
            try {
                JSONObject jsonObject = JSONObject.parseObject(message);
                handleStandardEvent(
                        jsonObject.getString("eventId"),
                        jsonObject.getString("event"),
                        jsonObject.getString("data")
                );
            } catch (Exception e) {
                log.error("处理EasyMrcp响应异常: {}", message, e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("EasyMrcp客户端异常", cause);
            ctx.close();
        }
    }

    private static class MessagePacketDecoder extends ByteToMessageDecoder {

        private static final int HEADER_LENGTH = 8;

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            in.markReaderIndex();
            if (in.readableBytes() < HEADER_LENGTH) {
                return;
            }

            int magic = in.readInt();
            if (magic != MAGIC_NUMBER) {
                in.resetReaderIndex();
                in.skipBytes(1);
                return;
            }

            int bodyLength = in.readInt();
            if (bodyLength < 0 || bodyLength > 10 * 1024 * 1024) {
                ctx.close();
                return;
            }

            if (in.readableBytes() < bodyLength) {
                in.resetReaderIndex();
                return;
            }

            byte[] bodyBytes = new byte[bodyLength];
            in.readBytes(bodyBytes);
            out.add(new String(bodyBytes, StandardCharsets.UTF_8));
        }
    }

    private static class MessagePacketEncoder extends MessageToByteEncoder<String> {
        @Override
        protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) {
            byte[] bodyBytes = msg.getBytes(StandardCharsets.UTF_8);
            out.writeInt(MAGIC_NUMBER);
            out.writeInt(bodyBytes.length);
            out.writeBytes(bodyBytes);
        }
    }
}

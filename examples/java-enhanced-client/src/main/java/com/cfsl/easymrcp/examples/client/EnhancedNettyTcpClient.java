package com.cfsl.easymrcp.examples.client;

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

/**
 * 纯 EasyMrcp TCP 接入示例客户端。
 *
 * 这个类只负责演示两件事：
 * 1. 如何按 EasyMrcp 的自定义 TCP 协议建立连接和收发消息
 * 2. 如何通过事件回调方式处理 ASR / TTS 相关事件
 *
 * 它不依赖 ESL，也不包含业务编排逻辑，适合作为最小 Java client 样板。
 */
@Slf4j
public class EnhancedNettyTcpClient implements AutoCloseable {

    /** EasyMrcp TCP 协议固定魔数：4 字节魔数 + 4 字节长度 + JSON 消息体。 */
    private static final int MAGIC_NUMBER = 0x66AABB99;

    /** EasyMrcp 服务地址。 */
    private final String serverHost;
    /** EasyMrcp 服务端口。 */
    private final int serverPort;
    /** 当前客户端使用的 Netty 事件循环。 */
    private final EventLoopGroup group;
    /** 标记 EventLoopGroup 是否由当前客户端创建，用于决定 disconnect 时是否需要一并关闭。 */
    private final boolean ownsGroup;
    /** 按事件类型注册回调，收到服务端事件后按类型分发。 */
    private final Map<TcpEventType, BiConsumer<String, String>> eventCallbacks = new ConcurrentHashMap<>();

    /** 当前 TCP 连接通道。 */
    private Channel channel;
    /** 是否已经建立连接。 */
    private volatile boolean connected;

    /** 每通会话的唯一标识，通常对应 callId / uuid。 */
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

    /**
     * 注册某个 EasyMrcp 事件的回调处理逻辑。
     *
     * 例如可以在 ClientConnect 后发送 DetectSpeech，
     * 在 RecognitionComplete 后回发 Speak。
     */
    public void registerEventCallback(TcpEventType eventType, BiConsumer<String, String> callback) {
        eventCallbacks.put(eventType, callback);
    }

    /**
     * 异步连接 EasyMrcp 服务。
     *
     * 连接成功后会自动发送一条 ClientConnect 事件，
     * connectParams 会作为该事件的 data 传给服务端。
     */
    public void connect(JSONObject connectParams) {
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 入站：按 EasyMrcp 自定义头格式解出 JSON 文本
                        pipeline.addLast(new MessagePacketDecoder());
                        // 出站：把 JSON 文本编码成 EasyMrcp 自定义头格式
                        pipeline.addLast(new MessagePacketEncoder());
                        // 业务事件处理器
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
                // 连接失败时只做必要清理，避免在 Netty 回调线程里调用阻塞式 disconnect。
                handleConnectFailure(channelFuture.channel());
            }
        });
    }

    /** 发送没有 eventId 的普通事件。 */
    public void sendEvent(TcpEventType eventType, String data) {
        sendEvent(null, eventType, data);
    }

    /**
     * 发送一条 EasyMrcp 事件。
     *
     * eventId 可用于把一次请求和后续回调串起来，例如区分某次具体的 Speak。
     */
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

    /**
     * 主动断开客户端连接。
     *
     * 这里会先尽量发送 ClientDisConnect，再关闭 channel。
     * 如果 EventLoopGroup 是当前客户端自己创建的，也会一并关闭。
     */
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

    /**
     * 连接阶段失败时的轻量清理。
     *
     * 这里只做必要状态回收，不走阻塞式 disconnect，
     * 避免在 Netty 回调线程中发生不安全的阻塞等待。
     */
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

    /**
     * 处理服务端回推的标准事件。
     *
     * 这里只做一层简单分发：按 event 名字映射到枚举，再触发外部注册的回调。
     */
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

    /** 处理 EasyMrcp TCP 通道上的入站消息。 */
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

    /**
     * 入站消息解码器。
     *
     * 协议格式固定为：4 字节魔数 + 4 字节长度 + UTF-8 JSON 文本。
     */
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

    /** 把 JSON 文本编码成 EasyMrcp TCP 协议消息。 */
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

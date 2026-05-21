package com.cfsl.easymrcp.examples.esl;

import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.examples.esl.client.EnhancedNettyTcpClient;
import com.cfsl.easymrcp.examples.esl.client.TcpEventType;
import com.cfsl.easymrcp.examples.esl.config.EasyMrcpDemoProperties;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SimpleEslEasyMrcpHandlerTests {

    @Test
    void applicationConfigDoesNotContainPromptTexts() throws Exception {
        Path configPath = Path.of("src/main/resources/application.yml");
        String config = Files.readString(configPath, StandardCharsets.UTF_8);

        assertFalse(config.contains("welcome-text:"));
        assertFalse(config.contains("timeout-text:"));
    }

    @Test
    void usesHardcodedWelcomePromptInsteadOfConfiguredValue() throws Exception {
        try (TestContext context = new TestContext()) {
            SimpleEslEasyMrcpHandler handler = context.createHandler();

            handler.start();
            context.client.fireEvent(TcpEventType.ClientConnect, "connect-event", "{\"msg\":\"SipInitSuccess\"}");

            assertEquals(TcpEventType.Speak, context.client.sentEvents.get(0).eventType);
            assertEquals("您好，请开始讲话。", context.client.sentEvents.get(0).data);
        }
    }

    @Test
    void usesHardcodedTimeoutPromptInsteadOfConfiguredValue() throws Exception {
        try (TestContext context = new TestContext()) {
            SimpleEslEasyMrcpHandler handler = context.createHandler();

            handler.start();
            context.client.fireEvent(TcpEventType.NoInputTimeout, "timeout-event", null);

            assertEquals(TcpEventType.Speak, context.client.sentEvents.get(0).eventType);
            assertEquals("您好，您还在线吗？", context.client.sentEvents.get(0).data);
        }
    }

    private static final class TestContext implements AutoCloseable {
        private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        private final EasyMrcpDemoProperties properties = new EasyMrcpDemoProperties();
        private final CapturingNettyTcpClient client = new CapturingNettyTcpClient();

        private TestContext() {
            properties.setHost("127.0.0.1");
            properties.setPort(9090);
        }

        private SimpleEslEasyMrcpHandler createHandler() throws Exception {
            SimpleEslEasyMrcpHandler handler = new SimpleEslEasyMrcpHandler(UUID.randomUUID().toString(), properties, eventLoopGroup);
            Field field = SimpleEslEasyMrcpHandler.class.getDeclaredField("mrcpClient");
            field.setAccessible(true);
            field.set(handler, client);
            return handler;
        }

        @Override
        public void close() {
            eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
        }
    }

    private static final class CapturingNettyTcpClient extends EnhancedNettyTcpClient {
        private final Map<TcpEventType, BiConsumer<String, String>> callbacks = new EnumMap<>(TcpEventType.class);
        private final List<SentEvent> sentEvents = new ArrayList<>();

        private CapturingNettyTcpClient() {
            super("127.0.0.1", 9090, "test-client", new NioEventLoopGroup(1));
        }

        @Override
        public void registerEventCallback(TcpEventType eventType, BiConsumer<String, String> callback) {
            callbacks.put(eventType, callback);
        }

        @Override
        public void connect(JSONObject connectParams) {
        }

        @Override
        public void sendEvent(String eventId, TcpEventType eventType, String data) {
            sentEvents.add(new SentEvent(eventType, data));
        }

        private void fireEvent(TcpEventType eventType, String eventId, String data) {
            callbacks.get(eventType).accept(eventId, data);
        }
    }

    private static final class SentEvent {
        private final TcpEventType eventType;
        private final String data;

        private SentEvent(TcpEventType eventType, String data) {
            this.eventType = eventType;
            this.data = data;
        }
    }
}

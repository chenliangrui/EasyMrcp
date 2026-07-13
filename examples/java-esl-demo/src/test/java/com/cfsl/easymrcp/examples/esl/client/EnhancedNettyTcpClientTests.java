package com.cfsl.easymrcp.examples.esl.client;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnhancedNettyTcpClientTests {

    @Test
    void ignoresSuccessfulTcpResponseWithoutEventField() {
        try (TestContext context = new TestContext()) {
            context.client.handleServerMessage("{\"id\":\"call-uuid\",\"code\":200,\"message\":\"Success\",\"data\":\"success\"}");

            assertNull(context.receivedEvent.get());
        }
    }

    @Test
    void dispatchesServerEventWithEventField() {
        try (TestContext context = new TestContext()) {
            context.client.handleServerMessage("{\"eventId\":\"event-1\",\"event\":\"ClientConnect\",\"data\":\"ready\"}");

            assertEquals("event-1:ready", context.receivedEvent.get());
        }
    }

    private static final class TestContext implements AutoCloseable {
        private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        private final AtomicReference<String> receivedEvent = new AtomicReference<>();
        private final EnhancedNettyTcpClient client = new EnhancedNettyTcpClient(
                "127.0.0.1", 9090, "call-uuid", eventLoopGroup);

        private TestContext() {
            client.registerEventCallback(TcpEventType.ClientConnect,
                    (eventId, data) -> receivedEvent.set(eventId + ":" + data));
        }

        @Override
        public void close() {
            eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
        }
    }
}

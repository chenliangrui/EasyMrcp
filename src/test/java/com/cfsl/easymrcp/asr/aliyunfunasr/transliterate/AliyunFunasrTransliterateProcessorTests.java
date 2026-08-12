package com.cfsl.easymrcp.asr.aliyunfunasr.transliterate;

import com.cfsl.easymrcp.asr.aliyunfunasr.AliyunFunasrConfig;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AliyunFunasrTransliterateProcessorTests {

    @Test
    void createBuildsRequestAndOpensWebsocketWithWorkspaceHeader() {
        RecordingFactory factory = new RecordingFactory();
        AliyunFunasrTransliterateProcessor processor = newProcessor(config("workspace-2"), factory);

        processor.create();

        assertNotNull(factory.client);
        assertEquals("https", factory.request.url().scheme());
        assertEquals("nls-gateway.example", factory.request.url().host());
        assertEquals("/ws", factory.request.url().encodedPath());
        assertEquals("Bearer test-api-key", factory.request.header("Authorization"));
        assertEquals("workspace-2", factory.request.header("X-DashScope-WorkSpace"));
        assertSame(factory.webSocket, factory.openedWebSocket);
        assertNotNull(factory.listener);
    }

    @Test
    void receiveForwardsBinaryPcmToWebsocketListenerPath() {
        RecordingFactory factory = new RecordingFactory();
        AliyunFunasrTransliterateProcessor processor = newProcessor(config("workspace-2"), factory);
        byte[] audio = new byte[]{5, 6, 7, 8};

        processor.create();
        factory.openListener();

        processor.receive(audio);

        assertArrayEquals(audio, factory.webSocket.binaryMessages.get(0).toByteArray());
    }

    @Test
    void sendEofDoesNotSendFinishTaskOrCloseSocket() {
        RecordingFactory factory = new RecordingFactory();
        AliyunFunasrTransliterateProcessor processor = newProcessor(config("workspace-2"), factory);

        processor.create();
        factory.openListener();

        processor.sendEof();

        assertEquals(List.of("run-task"), factory.webSocket.textMessages);
        assertEquals(0, factory.webSocket.closeCount);
    }

    @Test
    void asrCloseSendsFinishTaskAndWaitsForServerCompletion() {
        RecordingFactory factory = new RecordingFactory();
        AliyunFunasrTransliterateProcessor processor = newProcessor(config(""), factory);

        processor.create();
        factory.openListener();

        processor.asrClose();

        assertEquals(List.of("run-task", "finish-task"), factory.webSocket.textMessages);
        assertEquals(0, factory.webSocket.closeCount);
        assertNull(factory.request.header("X-DashScope-WorkSpace"));
    }

    private AliyunFunasrTransliterateProcessor newProcessor(
            AliyunFunasrConfig config,
            RecordingFactory factory) {
        AliyunFunasrTransliterateProcessor processor = new AliyunFunasrTransliterateProcessor(config, factory);
        processor.setCallId("call-456");
        processor.setCallback((action, message, audioDurationMs) -> {
        });
        processor.setInterruptEnable(new AtomicBoolean(true));
        processor.setPushAsrRealtimeResult(new AtomicBoolean(false));
        return processor;
    }

    private AliyunFunasrConfig config(String workspaceId) {
        AliyunFunasrConfig config = new AliyunFunasrConfig();
        config.setApiKey("test-api-key");
        config.setWebsocketUrl("wss://nls-gateway.example/ws");
        config.setWorkspaceId(workspaceId);
        config.setModel("fun-asr-realtime");
        config.setFormat("pcm");
        config.setSampleRate(16000);
        config.setMaxSentenceSilence(900);
        config.setHeartbeat(true);
        return config;
    }

    private static final class RecordingFactory implements AliyunFunasrTransliterateProcessor.WebSocketFactory {
        private OkHttpClient client;
        private Request request;
        private WebSocketListener listener;
        private WebSocket openedWebSocket;
        private final RecordingWebSocket webSocket = new RecordingWebSocket();

        @Override
        public OkHttpClient createClient() {
            client = new OkHttpClient.Builder()
                    .connectTimeout(1, TimeUnit.SECONDS)
                    .readTimeout(1, TimeUnit.SECONDS)
                    .writeTimeout(1, TimeUnit.SECONDS)
                    .build();
            return client;
        }

        @Override
        public WebSocket open(OkHttpClient client, Request request, WebSocketListener listener) {
            this.request = request;
            this.listener = listener;
            this.openedWebSocket = webSocket;
            return webSocket;
        }

        private void openListener() {
            listener.onOpen(webSocket, new Response.Builder()
                    .request(new Request.Builder().url(request.url()).build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(101)
                    .message("Switching Protocols")
                    .build());
        }
    }

    private static final class RecordingWebSocket implements WebSocket {
        private final List<String> textMessages = new ArrayList<>();
        private final List<ByteString> binaryMessages = new ArrayList<>();
        private int closeCount;
        private Integer closeCode;
        private String closeReason;

        @Override
        public Request request() {
            return new Request.Builder().url("http://localhost/ws").build();
        }

        @Override
        public long queueSize() {
            return 0;
        }

        @Override
        public boolean send(String text) {
            String action = com.google.gson.JsonParser.parseString(text)
                    .getAsJsonObject()
                    .getAsJsonObject("header")
                    .get("action")
                    .getAsString();
            textMessages.add(action);
            return true;
        }

        @Override
        public boolean send(ByteString bytes) {
            binaryMessages.add(bytes);
            return true;
        }

        @Override
        public boolean close(int code, String reason) {
            closeCount++;
            closeCode = code;
            closeReason = reason;
            return true;
        }

        @Override
        public void cancel() {
        }
    }
}

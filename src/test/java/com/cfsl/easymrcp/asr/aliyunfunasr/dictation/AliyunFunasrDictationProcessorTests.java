package com.cfsl.easymrcp.asr.aliyunfunasr.dictation;

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

class AliyunFunasrDictationProcessorTests {

    @Test
    void createBuildsRequestAndOpensWebsocketWithWorkspaceHeader() {
        RecordingFactory factory = new RecordingFactory();
        AliyunFunasrDictationProcessor processor = newProcessor(config("workspace-1"), factory);

        processor.create();

        assertNotNull(factory.client);
        assertEquals("https", factory.request.url().scheme());
        assertEquals("nls-gateway.example", factory.request.url().host());
        assertEquals("/ws", factory.request.url().encodedPath());
        assertEquals("Bearer test-api-key", factory.request.header("Authorization"));
        assertEquals("workspace-1", factory.request.header("X-DashScope-WorkSpace"));
        assertSame(factory.webSocket, factory.openedWebSocket);
        assertNotNull(factory.listener);
    }

    @Test
    void receiveForwardsBinaryPcmToWebsocketListenerPath() {
        RecordingFactory factory = new RecordingFactory();
        AliyunFunasrDictationProcessor processor = newProcessor(config("workspace-1"), factory);
        byte[] audio = new byte[]{1, 2, 3, 4};

        processor.create();
        factory.openListener();

        processor.receive(audio);

        assertArrayEquals(audio, factory.webSocket.binaryMessages.get(0).toByteArray());
    }

    @Test
    void sendEofSendsFinishTaskWithoutClosingSocket() {
        RecordingFactory factory = new RecordingFactory();
        AliyunFunasrDictationProcessor processor = newProcessor(config("workspace-1"), factory);

        processor.create();
        factory.openListener();

        processor.sendEof();

        assertEquals(2, factory.webSocket.textMessages.size());
        assertEquals("finish-task", factory.webSocket.textMessages.get(1));
        assertEquals(0, factory.webSocket.closeCount);
    }

    @Test
    void asrCloseSendsBestEffortFinishTaskAndClosesSocket() {
        RecordingFactory factory = new RecordingFactory();
        AliyunFunasrDictationProcessor processor = newProcessor(config(""), factory);

        processor.create();
        factory.openListener();

        processor.asrClose();

        assertEquals(List.of("run-task", "finish-task"), factory.webSocket.textMessages);
        assertEquals(1, factory.webSocket.closeCount);
        assertEquals(1000, factory.webSocket.closeCode);
        assertEquals("正常关闭", factory.webSocket.closeReason);
        assertNull(factory.request.header("X-DashScope-WorkSpace"));
    }

    private AliyunFunasrDictationProcessor newProcessor(
            AliyunFunasrConfig config,
            AliyunFunasrDictationProcessor.WebSocketFactory factory) {
        AliyunFunasrDictationProcessor processor = new AliyunFunasrDictationProcessor(config, factory);
        processor.setCallId("call-123");
        processor.setCallback((action, message) -> {
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

    private static final class RecordingFactory implements AliyunFunasrDictationProcessor.WebSocketFactory {
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

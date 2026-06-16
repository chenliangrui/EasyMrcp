package com.cfsl.easymrcp.asr.aliyunfunasr.dictation;

import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.asr.aliyunfunasr.AliyunFunasrConfig;
import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpEventType;
import com.cfsl.easymrcp.utils.SipUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AliyunFunasrDictationWsClientTests {

    private Object originalNotifier;

    @BeforeEach
    void captureStaticNotifier() {
        originalNotifier = ReflectionTestUtils.getField(SipUtils.class, "tcpClientNotifier");
    }

    @AfterEach
    void restoreStaticNotifier() {
        ReflectionTestUtils.setField(SipUtils.class, "tcpClientNotifier", originalNotifier);
    }

    @Test
    void firstInterimTriggersInterruptOncePerParagraph() {
        List<String> callbacks = new ArrayList<>();
        AliyunFunasrDictationWsClient client = newClient(new CountDownLatch(1), new AtomicBoolean(true), new AtomicBoolean(false), false, recordCallbacks(callbacks));
        RecordingWebSocket webSocket = new RecordingWebSocket();
        client.onOpen(webSocket, websocketResponse());

        client.onMessage(webSocket, resultMessage("hello", false, false));
        client.onMessage(webSocket, resultMessage("hello again", false, false));

        assertEquals(List.of(ASRConstant.Interrupt + "|hello"), callbacks);
    }

    @Test
    void finalSentenceTriggersResultAndNextInterimCanInterruptAgain() {
        List<String> callbacks = new ArrayList<>();
        AliyunFunasrDictationWsClient client = newClient(new CountDownLatch(1), new AtomicBoolean(true), new AtomicBoolean(false), false, recordCallbacks(callbacks));
        RecordingWebSocket webSocket = new RecordingWebSocket();
        client.onOpen(webSocket, websocketResponse());

        client.onMessage(webSocket, resultMessage("hello", false, false));
        client.onMessage(webSocket, resultMessage("hello world", false, true));
        client.onMessage(webSocket, resultMessage("next turn", false, false));

        assertEquals(List.of(
                ASRConstant.Interrupt + "|hello",
                ASRConstant.Result + "|hello world",
                ASRConstant.Interrupt + "|next turn"
        ), callbacks);
    }

    @Test
    void realtimePushSkipsFinalSentence() {
        TcpClientNotifier notifier = mock(TcpClientNotifier.class);
        ReflectionTestUtils.setField(SipUtils.class, "tcpClientNotifier", notifier);
        List<String> callbacks = new ArrayList<>();
        AliyunFunasrDictationWsClient client = newClient(new CountDownLatch(1), new AtomicBoolean(false), new AtomicBoolean(true), false, recordCallbacks(callbacks));
        RecordingWebSocket webSocket = new RecordingWebSocket();
        client.onOpen(webSocket, websocketResponse());

        client.onMessage(webSocket, resultMessage("streaming text", false, false));
        client.onMessage(webSocket, resultMessage("final text", false, true));

        verify(notifier).sendEvent(eq("call-123"), isNull(), eq(TcpEventType.AsrRealTimeResult),
                eq("{\"asrEngine\":\"" + EMConstant.ALIYUN_FUNASR + "\",\"asrResult\":\"streaming text\"}"));
        verify(notifier, never()).sendEvent(anyString(), nullable(String.class), eq(TcpEventType.AsrRealTimeResult),
                eq("{\"asrEngine\":\"" + EMConstant.ALIYUN_FUNASR + "\",\"asrResult\":\"final text\"}"));
        assertEquals(List.of(ASRConstant.Result + "|final text"), callbacks);
    }

    @Test
    void taskStartedReleasesLatchAndTaskFailedStopsLateResults() {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> callbacks = new ArrayList<>();
        AliyunFunasrDictationWsClient client = newClient(latch, new AtomicBoolean(true), new AtomicBoolean(true), false, recordCallbacks(callbacks));
        RecordingWebSocket webSocket = new RecordingWebSocket();
        client.onOpen(webSocket, websocketResponse());

        client.onMessage(webSocket, eventMessage("task-started"));
        client.onMessage(webSocket, eventMessage("task-failed"));
        client.onMessage(webSocket, resultMessage("stale text", false, true));

        assertEquals(0L, latch.getCount());
        assertTrue(callbacks.isEmpty());
    }

    @Test
    void rejectedRunTaskSendReleasesLatchImmediately() {
        CountDownLatch latch = new CountDownLatch(1);
        AliyunFunasrDictationWsClient client = newClient(latch, new AtomicBoolean(true), new AtomicBoolean(false), false, (action, message) -> {
        });
        RecordingWebSocket webSocket = new RecordingWebSocket();
        webSocket.queueTextSendResult(false);

        client.onOpen(webSocket, websocketResponse());
        client.onMessage(webSocket, resultMessage("stale text", false, true));

        assertEquals(0L, latch.getCount());
        assertEquals(0, webSocket.textMessages.size());
        assertTrue(webSocket.closeCount > 0 || webSocket.cancelCount > 0);
    }

    private AliyunFunasrDictationWsClient newClient(
            CountDownLatch latch,
            AtomicBoolean interruptEnable,
            AtomicBoolean pushRealtime,
            boolean stop,
            AsrCallback callback) {
        return new AliyunFunasrDictationWsClient(
                config(),
                "task-123",
                "call-123",
                callback,
                stop,
                latch,
                interruptEnable,
                pushRealtime
        );
    }

    private AsrCallback recordCallbacks(List<String> callbacks) {
        return (action, message) -> callbacks.add(action + "|" + message);
    }

    private AliyunFunasrConfig config() {
        AliyunFunasrConfig config = new AliyunFunasrConfig();
        config.setModel("fun-asr-realtime");
        config.setFormat("pcm");
        config.setSampleRate(16000);
        config.setMaxSentenceSilence(900);
        config.setVocabularyId("vocab-1");
        config.setLanguageHints("zh");
        config.setSemanticPunctuationEnabled(true);
        config.setHeartbeat(true);
        return config;
    }

    private Response websocketResponse() {
        return new Response.Builder()
                .request(new Request.Builder().url("http://localhost/ws").build())
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .build();
    }

    private String eventMessage(String event) {
        JsonObject message = new JsonObject();
        JsonObject header = new JsonObject();
        header.addProperty("task_id", "task-123");
        header.addProperty("event", event);
        header.add("attributes", new JsonObject());
        message.add("header", header);
        message.add("payload", new JsonObject());
        return message.toString();
    }

    private String resultMessage(String text, boolean heartbeat, boolean sentenceEnd) {
        JsonObject message = JsonParser.parseString(eventMessage("result-generated")).getAsJsonObject();
        JsonObject payload = new JsonObject();
        JsonObject output = new JsonObject();
        JsonObject sentence = new JsonObject();
        sentence.addProperty("text", text);
        sentence.addProperty("heartbeat", heartbeat);
        sentence.addProperty("sentence_end", sentenceEnd);
        output.add("sentence", sentence);
        payload.add("output", output);
        message.add("payload", payload);
        return message.toString();
    }

    private static final class RecordingWebSocket implements WebSocket {
        private final List<String> textMessages = new ArrayList<>();
        private final Deque<Boolean> textSendResults = new ArrayDeque<>();
        private int closeCount;
        private int cancelCount;

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
            boolean accepted = textSendResults.isEmpty() ? true : textSendResults.removeFirst();
            if (accepted) {
                textMessages.add(text);
            }
            return accepted;
        }

        @Override
        public boolean send(ByteString bytes) {
            return true;
        }

        @Override
        public boolean close(int code, String reason) {
            closeCount++;
            return true;
        }

        @Override
        public void cancel() {
            cancelCount++;
        }

        private void queueTextSendResult(boolean accepted) {
            textSendResults.addLast(accepted);
        }
    }
}

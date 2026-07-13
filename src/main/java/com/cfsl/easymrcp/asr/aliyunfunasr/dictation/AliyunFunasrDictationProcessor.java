package com.cfsl.easymrcp.asr.aliyunfunasr.dictation;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.asr.aliyunfunasr.AliyunFunasrConfig;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.UUID;

/**
 * 阿里云 FunASR 在线一句话识别处理器。
 * 负责连接创建、音频转发和资源回收，协议细节由 {@link AliyunFunasrDictationWsClient} 处理。
 */
public class AliyunFunasrDictationProcessor extends AsrHandler {

    private final AliyunFunasrConfig config;
    private final WebSocketFactory webSocketFactory;

    private OkHttpClient client;
    private AliyunFunasrDictationWsClient wsClient;
    private WebSocket webSocket;

    public AliyunFunasrDictationProcessor(AliyunFunasrConfig aliyunFunasrConfig) {
        this(aliyunFunasrConfig, new DefaultWebSocketFactory());
    }

    AliyunFunasrDictationProcessor(
            AliyunFunasrConfig aliyunFunasrConfig,
            WebSocketFactory webSocketFactory) {
        this.config = aliyunFunasrConfig;
        this.webSocketFactory = webSocketFactory;
    }

    @Override
    public void create() {
        AsrCallback callbackProxy = (action, msg) -> {
            AsrCallback callback = getCallback();
            if (callback != null) {
                callback.apply(action, msg);
            }
        };

        wsClient = new AliyunFunasrDictationWsClient(
                config,
                UUID.randomUUID().toString(),
                getCallId(),
                callbackProxy,
                stop,
                getCountDownLatch(),
                getInterruptEnable(),
                getPushAsrRealtimeResult()
        );
        client = webSocketFactory.createClient();

        Request.Builder builder = new Request.Builder()
                .url(config.getWebsocketUrl())
                .header("Authorization", "Bearer " + config.getApiKey());
        if (config.getWorkspaceId() != null && !config.getWorkspaceId().trim().isEmpty()) {
            builder.header("X-DashScope-WorkSpace", config.getWorkspaceId());
        }

        webSocket = webSocketFactory.open(client, builder.build(), wsClient);
    }

    @Override
    public void receive(byte[] pcmData) {
        if (wsClient != null) {
            wsClient.sendAudio(pcmData);
        }
    }

    @Override
    public void sendEof() {
        if (wsClient != null) {
            wsClient.sendFinishTask();
        }
    }

    @Override
    public void asrClose() {
        if (wsClient != null && !wsClient.isFinishTaskSent()) {
            wsClient.sendFinishTask();
        }
        if (wsClient != null) {
            wsClient.closeSocket("正常关闭");
        } else if (webSocket != null) {
            webSocket.close(1000, "正常关闭");
        }
        webSocket = null;
        wsClient = null;

        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
            client = null;
        }
    }

    interface WebSocketFactory {
        OkHttpClient createClient();

        WebSocket open(OkHttpClient client, Request request, WebSocketListener listener);
    }

    private static final class DefaultWebSocketFactory implements WebSocketFactory {
        @Override
        public OkHttpClient createClient() {
            return new OkHttpClient();
        }

        @Override
        public WebSocket open(OkHttpClient client, Request request, WebSocketListener listener) {
            return client.newWebSocket(request, listener);
        }
    }
}

package com.cfsl.easymrcp.asr.aliyunfunasr.transliterate;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.asr.aliyunfunasr.AliyunFunasrConfig;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.UUID;

/**
 * 阿里云 FunASR 长时间转写处理器。
 * 负责长连接创建、音频持续转发和资源回收，协议细节由 {@link AliyunFunasrTransliterateWsClient} 处理。
 */
public class AliyunFunasrTransliterateProcessor extends AsrHandler {

    private final AliyunFunasrConfig config;
    private final WebSocketFactory webSocketFactory;

    private OkHttpClient client;
    private AliyunFunasrTransliterateWsClient wsClient;
    private WebSocket webSocket;

    public AliyunFunasrTransliterateProcessor(AliyunFunasrConfig aliyunFunasrConfig) {
        this(aliyunFunasrConfig, new DefaultWebSocketFactory());
    }

    AliyunFunasrTransliterateProcessor(
            AliyunFunasrConfig aliyunFunasrConfig,
            WebSocketFactory webSocketFactory) {
        this.config = aliyunFunasrConfig;
        this.webSocketFactory = webSocketFactory;
    }

    /**
     * 建立阿里云 FunASR 长转写连接，并注入当前通话上下文。
     */
    @Override
    public void create() {
        AsrCallback callbackProxy = (action, msg, audioDurationMs) -> {
            AsrCallback callback = getCallback();
            if (callback != null) {
                callback.apply(action, msg, audioDurationMs);
            }
        };

        wsClient = new AliyunFunasrTransliterateWsClient(
                config,
                UUID.randomUUID().toString(),
                getCallId(),
                callbackProxy,
                stop,
                getCountDownLatch(),
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

    /**
     * 将整段通话的 PCM 音频持续发送到阿里云识别连接。
     */
    @Override
    public void receive(byte[] pcmData) {
        if (wsClient != null) {
            wsClient.sendAudio(pcmData);
        }
    }

    /**
     * 长时间转写模式不在此处主动收句，finish-task 在关闭阶段统一发送。
     */
    @Override
    public void sendEof() {
    }

    /**
     * 通话结束时补发 finish-task，等待服务端返回最终计费时长后关闭长连接。
     */
    @Override
    public void asrClose() {
        if (wsClient != null && !wsClient.isFinishTaskSent()) {
            wsClient.sendFinishTask();
        }
        if (wsClient == null && webSocket != null) {
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

package com.example.easymrcp.asr.xfyun;

import com.example.easymrcp.asr.AsrHandler;
import com.example.easymrcp.asr.xfyun.transliterate.DraftWithOrigin;
import com.example.easymrcp.asr.xfyun.transliterate.XfyunWsClient;
import com.example.easymrcp.mrcp.AsrCallback;
import okhttp3.WebSocket;
import org.java_websocket.enums.ReadyState;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;

public class XfyunAsrProcessor extends AsrHandler {
    // appid
    private static final String APPID = "c22aeabc";
    // appid对应的secret_key
    private static final String SECRET_KEY = "a4e1ea9583139481b2bc47c276869cd9";
    // 请求地址
    private static final String HOST = "rtasr.xfyun.cn/v1/ws";
    private static final String BASE_URL = "wss://" + HOST;
    private static final String ORIGIN = "https://" + HOST;


    AsrCallback xfyunAsrCallback;
    XfyunWsClient xfyunWsClient;
    XfyunWsClient.MyWebSocketClient client;

    @Override
    public void create() {
        try {
            URI url = new URI(BASE_URL + XfyunWsClient.getHandShakeParams(APPID, SECRET_KEY));
            DraftWithOrigin draft = new DraftWithOrigin(ORIGIN);
            CountDownLatch handshakeSuccess = new CountDownLatch(1);
            CountDownLatch connectClose = new CountDownLatch(1);
            client = new XfyunWsClient.MyWebSocketClient(url, draft, handshakeSuccess, connectClose);

            client.connect();

            while (!client.getReadyState().equals(ReadyState.OPEN)) {
                System.out.println(XfyunWsClient.getCurrentTimeStr() + "\t连接中");
                Thread.sleep(1000);
            }

            // 等待握手成功
            handshakeSuccess.await();
        } catch (InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
        System.out.println("开始发送音频数据");

        xfyunAsrCallback = new AsrCallback() {
            @Override
            public void apply(String msg) {
                getCallback().apply(msg);
            }
        };
        xfyunWsClient = new XfyunWsClient(xfyunAsrCallback, stop, client);
    }

    @Override
    public void receive(byte[] pcmData) {
        xfyunWsClient.sendBuffer(pcmData);
    }

    @Override
    public void asrClose() {
        xfyunWsClient.sendEof();
    }
}

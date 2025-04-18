package com.example.easymrcp.asr.xfyun.transliterate;

import com.example.easymrcp.asr.AsrHandler;
import com.example.easymrcp.mrcp.AsrCallback;
import org.java_websocket.enums.ReadyState;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;

/**
 * TODO 精准程度需要进一步优化
 * 讯飞云实时语音转写（长时间语音转写）
 */
public class XfyunTransliterateAsrProcessor extends AsrHandler {
    // appid
    private static final String APPID = "c22aeabc";
    // appid对应的secret_key
    private static final String SECRET_KEY = "a4e1ea9583139481b2bc47c276869cd9";
    // 请求地址
    private static final String HOST = "rtasr.xfyun.cn/v1/ws";
    private static final String BASE_URL = "wss://" + HOST;
    private static final String ORIGIN = "https://" + HOST;


    AsrCallback xfyunAsrCallback;
    XfyunTransliterateWsClient xfyunWsClient;
    XfyunTransliterateWsClient.MyWebSocketClient client;

    @Override
    public void create() {
        try {
            URI url = new URI(BASE_URL + XfyunTransliterateWsClient.getHandShakeParams(APPID, SECRET_KEY));
            DraftWithOrigin draft = new DraftWithOrigin(ORIGIN);
            client = new XfyunTransliterateWsClient.MyWebSocketClient(url, draft, getCountDownLatch());
            client.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        System.out.println("开始发送音频数据");

        xfyunAsrCallback = new AsrCallback() {
            @Override
            public void apply(String msg) {
                getCallback().apply(msg);
            }
        };
        xfyunWsClient = new XfyunTransliterateWsClient(xfyunAsrCallback, stop, client);
    }

    @Override
    public void receive(byte[] pcmData) {
        xfyunWsClient.sendBuffer(pcmData);
    }

    @Override
    public void sendEof() {

    }

    @Override
    public void asrClose() {
        xfyunWsClient.sendEof();
        xfyunWsClient.client.close();
    }
}

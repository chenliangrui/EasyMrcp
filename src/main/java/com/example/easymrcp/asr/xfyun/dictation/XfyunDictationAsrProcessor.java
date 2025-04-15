package com.example.easymrcp.asr.xfyun.dictation;

import com.example.easymrcp.asr.AsrHandler;
import com.example.easymrcp.mrcp.AsrCallback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class XfyunDictationAsrProcessor extends AsrHandler {
    private static final String hostUrl = "https://iat-api.xfyun.cn/v2/iat"; //中英文，http url 不支持解析 ws/wss schema
    // private static final String hostUrl = "https://iat-niche-api.xfyun.cn/v2/iat";//小语种
    private static final String appid = "c22aeabc"; //在控制台-我的应用获取
    private static final String apiSecret = "NjAwYWYyZDQ5ZjJjNjZhY2UzMWJjMThl"; //在控制台-我的应用-语音听写（流式版）获取
    private static final String apiKey = "f23da979c109e5c31c0dc9dd5d3052a5"; //在控制台-我的应用-语音听写（流式版）获取
    AsrCallback funasrCallback;
    XfyunDictationWsClient xfyunWsClient;
    WebSocket webSocket;

    @Override
    public void create() {
        // 构建鉴权url
        String authUrl = null;
        try {
            authUrl = XfyunDictationWsClient.getAuthUrl(hostUrl, apiKey, apiSecret);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        OkHttpClient client = new OkHttpClient.Builder().build();
        //将url中的 schema http://和https://分别替换为ws:// 和 wss://
        String url = authUrl.toString().replace("http://", "ws://").replace("https://", "wss://");
        //System.out.println(url);
        Request request = new Request.Builder().url(url).build();
        // System.out.println(client.newCall(request).execute());
        //System.out.println("url===>" + url);
        funasrCallback = new AsrCallback() {
            @Override
            public void apply(String msg) {
                getCallback().apply(msg);
            }
        };
        xfyunWsClient = new XfyunDictationWsClient(funasrCallback, stop, getCountDownLatch());
        webSocket = client.newWebSocket(request, xfyunWsClient);
    }

    @Override
    public void receive(byte[] pcmData) {
        xfyunWsClient.sendBuffer(pcmData);
    }

    @Override
    public void asrClose() {
        //TODO 不应该在此处调用，应该在vad结束说话后调用
        xfyunWsClient.sendEof();
    }
}
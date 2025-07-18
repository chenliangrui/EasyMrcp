package com.cfsl.easymrcp.asr.xfyun.dictation;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.asr.xfyun.XfyunAsrConfig;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞云实时语音听写（一句话语音识别）
 * https://www.xfyun.cn/services/voicedictation
 */
@Slf4j
public class XfyunDictationAsrProcessor extends AsrHandler {
    private String hostUrl; //中英文，http url 不支持解析 ws/wss schema
    // private static final String hostUrl = "https://iat-niche-api.xfyun.cn/v2/iat";//小语种
    private String appid; //在控制台-我的应用获取
    private String apiSecret; //在控制台-我的应用-语音听写（流式版）获取
    private String apiKey; //在控制台-我的应用-语音听写（流式版）获取
    AsrCallback xfyunCallback;
    XfyunDictationWsClient xfyunWsClient;
    WebSocket webSocket;
    private OkHttpClient client;

    public XfyunDictationAsrProcessor(XfyunAsrConfig xfyunAsrConfig) {
        this.hostUrl = xfyunAsrConfig.getHostUrl();
        this.appid = xfyunAsrConfig.getAPPID();
        this.apiSecret = xfyunAsrConfig.getAPISecret();
        this.apiKey = xfyunAsrConfig.getAPIKey();
    }

    @Override
    public void create() {
        // 构建鉴权url
        String authUrl = null;
        try {
            authUrl = XfyunDictationWsClient.getAuthUrl(hostUrl, apiKey, apiSecret);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        //将url中的 schema http://和https://分别替换为ws:// 和 wss://
        String url = authUrl.toString().replace("http://", "ws://").replace("https://", "wss://");
        //System.out.println(url);
        Request request = new Request.Builder().url(url).build();
        // System.out.println(client.newCall(request).execute());
        //System.out.println("url===>" + url);
        xfyunCallback = new AsrCallback() {
            @Override
            public void apply(String action, String msg) {
                getCallback().apply(action, msg);
            }
        };
        xfyunWsClient = new XfyunDictationWsClient(xfyunCallback, stop, getCountDownLatch());
        xfyunWsClient.setHostUrl(hostUrl);
        xfyunWsClient.setAppid(appid);
        xfyunWsClient.setApiSecret(apiSecret);
        xfyunWsClient.setApiKey(apiKey);
        webSocket = client.newWebSocket(request, xfyunWsClient);
    }

    @Override
    public void receive(byte[] pcmData) {
        if (xfyunWsClient != null) {
            xfyunWsClient.sendBuffer(pcmData);
        }
    }

    @Override
    public void sendEof() {
        if (xfyunWsClient != null) {
            xfyunWsClient.sendEof();
        }
    }

    @Override
    public void asrClose() {
        if (webSocket != null) {
            webSocket.close(1000, "Normal closure");
            log.info("Xfyun webSocket connection closed");
            webSocket = null;
        }
        
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
            log.info("Xfyun OkHttpClient resources released");
            client = null;
        }
        
        xfyunWsClient = null;
    }
}
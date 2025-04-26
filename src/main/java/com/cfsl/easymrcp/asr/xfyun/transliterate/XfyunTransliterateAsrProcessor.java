package com.cfsl.easymrcp.asr.xfyun.transliterate;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.asr.xfyun.XfyunAsrConfig;
import com.cfsl.easymrcp.mrcp.AsrCallback;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * TODO 精准程度需要进一步优化
 * 讯飞云实时语音转写（长时间语音转写）
 */
public class XfyunTransliterateAsrProcessor extends AsrHandler {
    // appid
    private String APPID;
    // appid对应的secret_key
    private String APIKey;
    // 请求地址
    private String HOST;
    AsrCallback xfyunAsrCallback;
    XfyunTransliterateWsClient xfyunWsClient;
    XfyunTransliterateWsClient.MyWebSocketClient client;

    public XfyunTransliterateAsrProcessor(XfyunAsrConfig xfyunAsrConfig) {
        this.APPID = xfyunAsrConfig.getAPPID();
        this.APIKey = xfyunAsrConfig.getAPIKey();
        this.HOST = xfyunAsrConfig.getHostUrl();
    }

    @Override
    public void create() {
        try {
            String BASE_URL = "wss://" + HOST;
            String ORIGIN = "https://" + HOST;
            URI url = new URI(BASE_URL + XfyunTransliterateWsClient.getHandShakeParams(APPID, APIKey));
            DraftWithOrigin draft = new DraftWithOrigin(ORIGIN);
            client = new XfyunTransliterateWsClient.MyWebSocketClient(url, draft, getCountDownLatch());
            client.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

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

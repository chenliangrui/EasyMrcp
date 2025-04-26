package com.cfsl.easymrcp.asr.xfyun.transliterate;

import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshakeBuilder;

import javax.validation.constraints.NotNull;

/**
 * 讯飞云官方提供示例代码
 */
@SuppressWarnings("deprecation")
public class DraftWithOrigin extends Draft_6455 {

    private String originUrl;

    public DraftWithOrigin(String originUrl) {
        this.originUrl = originUrl;
    }

    @Override
    public Draft copyInstance() {
        System.out.println(originUrl);
        return new DraftWithOrigin(originUrl);
    }

    @NotNull
    @Override
    public ClientHandshakeBuilder postProcessHandshakeRequestAsClient(@NotNull ClientHandshakeBuilder request) {
        super.postProcessHandshakeRequestAsClient(request);
        request.put("Origin", originUrl);
        return request;
    }
}
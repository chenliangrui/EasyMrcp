package com.cfsl.easymrcp.mrcp;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpEventName;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.MrcpResponse;
import org.mrcp4j.message.request.MrcpRequestFactory;
import org.mrcp4j.message.request.StopRequest;
import org.mrcp4j.server.MrcpSession;
import org.mrcp4j.server.provider.SpeechSynthRequestHandler;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

/**
 * Mrcp协议中tts处理
 */
@Slf4j
public class MrcpSpeechSynthChannel implements SpeechSynthRequestHandler {
    TtsHandler ttsHandler;

    public MrcpSpeechSynthChannel(TtsHandler ttsHandler) {
        this.ttsHandler = ttsHandler;
    }

    @Override
    public MrcpResponse speak(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        String contentType = unimplementedRequest.getContentType();
        TtsCallback callback = new TtsCallback() {
            @Override
            public void apply(String msg) {
                try {
                    MrcpEvent eventComplete = mrcpSession.createEvent(
                            MrcpEventName.SPEAK_COMPLETE,
                            MrcpRequestState.COMPLETE
                    );
                    mrcpSession.postEvent(eventComplete);
                } catch (Exception e) {
                    log.error("postEvent error", e);
                }
            }
        };
        ttsHandler.setCallback(callback);
        if (contentType.equalsIgnoreCase("text/plain")) {
            String text = unimplementedRequest.getContent();
//            String s = eslBodyStrConvert(text);
//            //TODO 过滤非法字符
            text = text.replaceAll("[\\r\\n]", "");
            ttsHandler.transmit(text);
        }
        short statusCode = MrcpResponse.STATUS_SUCCESS;
        MrcpResponse response = mrcpSession.createResponse(statusCode, MrcpRequestState.IN_PROGRESS);
        return response;
    }

    public static String eslBodyStrConvert(String eslBodyStr) {
        byte[] bytes = new byte[0];
        try {
            bytes = eslBodyStr.getBytes("Unicode");
        } catch (UnsupportedEncodingException e) {
        }
        List<Byte> list = new LinkedList<>();
        for (int i = 0; i < bytes.length; ++i) {
            if (i % 2 == 0 || i == 1) {
                continue;
            }
            list.add(bytes[i]);
        }
        byte[] bytes2 = new byte[list.size()];
        for (int k = 0; k < list.size(); ++k) {
            bytes2[k] = list.get(k);
        }
        return new String(bytes2, StandardCharsets.UTF_8);
    }

    @Override
    public MrcpResponse stop(StopRequest stopRequest, MrcpSession mrcpSession) {
        log.info("MrcpSpeechSynthChannel stop");
        MrcpRequestState requestState = MrcpRequestState.COMPLETE;
        short statusCode = -1;
        //TODO 语音识别打断,需要关闭其他资源？
        ttsHandler.stop();
        statusCode = MrcpResponse.STATUS_SUCCESS;

        //TODO: set Active-Request-Id-List header

        return mrcpSession.createResponse(statusCode, requestState);
    }

    @Override
    public MrcpResponse pause(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        return null;
    }

    @Override
    public MrcpResponse resume(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        return null;
    }

    @Override
    public MrcpResponse bargeInOccurred(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        return null;
    }

    @Override
    public MrcpResponse control(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        return null;
    }

    @Override
    public MrcpResponse defineLexicon(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        return null;
    }

    @Override
    public MrcpResponse setParams(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        return null;
    }

    @Override
    public MrcpResponse getParams(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        return null;
    }
}

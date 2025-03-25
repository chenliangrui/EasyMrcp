package com.example.easymrcp.mrcp;

import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpEventName;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.MrcpResponse;
import org.mrcp4j.message.header.CompletionCause;
import org.mrcp4j.message.header.MrcpHeader;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.message.request.MrcpRequestFactory;
import org.mrcp4j.message.request.StopRequest;
import org.mrcp4j.server.MrcpSession;
import org.mrcp4j.server.provider.SpeechSynthRequestHandler;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

@Slf4j
public class MrcpSpeechSynthChannel implements SpeechSynthRequestHandler {
    @Override
    public MrcpResponse speak(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        String contentType = unimplementedRequest.getContentType();
        if (contentType.equalsIgnoreCase("text/plain")) {
            String text = unimplementedRequest.getContent();
            String s = eslBodyStrConvert(text);
            System.out.println(s);
        }
        MrcpEvent event = mrcpSession.createEvent(MrcpEventName.START_OF_INPUT, MrcpRequestState.IN_PROGRESS);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(3000);
                    MrcpEvent eventComplete = mrcpSession.createEvent(
                            MrcpEventName.SPEAK_COMPLETE,
                            MrcpRequestState.COMPLETE
                    );
                    CompletionCause completionCause = new CompletionCause((short) 0, "normal");
                    MrcpHeader completionCauseHeader = MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause);
                    event.addHeader(completionCauseHeader);
                    mrcpSession.postEvent(eventComplete);
                } catch (Exception e) {
                    log.error("postEvent error", e);
                }
            }
        }).start();
        short statusCode = MrcpResponse.STATUS_SUCCESS;
        MrcpResponse response = mrcpSession.createResponse(statusCode, MrcpRequestState.IN_PROGRESS);
        return response;
    }

    public static String eslBodyStrConvert(String eslBodyStr) {
        byte[] bytes = new byte[0];
        try {
            bytes = eslBodyStr.getBytes("Unicode");
        } catch (UnsupportedEncodingException e) {}
        List<Byte> list = new LinkedList<>();
        for (int i=0; i<bytes.length; ++i) {
            if (i % 2 == 0 || i == 1) {
                continue;
            }
            list.add(bytes[i]);
        }
        byte[] bytes2 = new byte[list.size()];
        for (int k = 0; k<list.size(); ++k) {
            bytes2[k] = list.get(k);
        }
        return new String(bytes2, StandardCharsets.UTF_8);
    }

    @Override
    public MrcpResponse stop(StopRequest stopRequest, MrcpSession mrcpSession) {
        return null;
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

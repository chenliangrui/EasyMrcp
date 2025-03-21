package com.example.easymrcp.mrcp;

import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpEventName;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.MrcpResponse;
import org.mrcp4j.message.header.CompletionCause;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.message.request.MrcpRequestFactory;
import org.mrcp4j.message.request.StartInputTimersRequest;
import org.mrcp4j.message.request.StopRequest;
import org.mrcp4j.server.MrcpSession;
import org.mrcp4j.server.provider.RecogOnlyRequestHandler;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class MrcpRecogChannel implements RecogOnlyRequestHandler {
    @Override
    public MrcpResponse defineGrammar(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        log.info("MrcpRecogChannel.defineGrammar");
        MrcpResponse response = mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.COMPLETE);
        return response;
    }

    @Override
    public MrcpResponse recognize(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        log.info("MrcpRecogChannel recognize");
        MrcpResponse response = mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.IN_PROGRESS);
        // 开始发送语音
        new Thread(new Runnable() {
            @Override
            public void run() {
                MrcpEvent event = mrcpSession.createEvent(MrcpEventName.START_OF_INPUT, MrcpRequestState.IN_PROGRESS);
                try {
                    mrcpSession.postEvent(event);
                    // 模拟语音识别完成
                    Thread.sleep(30000);
                    MrcpEvent eventComplete = mrcpSession.createEvent(MrcpEventName.RECOGNITION_COMPLETE, MrcpRequestState.COMPLETE);
                    CompletionCause completionCause = new CompletionCause((short) 0, "success");
                    eventComplete.addHeader(MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause));
                    eventComplete.setContent("text/plain", null, "你好");
                    mrcpSession.postEvent(eventComplete);
                } catch (TimeoutException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
        return response;
    }

    @Override
    public MrcpResponse interpret(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        return null;
    }

    @Override
    public MrcpResponse getResult(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        return null;
    }

    @Override
    public MrcpResponse startInputTimers(StartInputTimersRequest startInputTimersRequest, MrcpSession mrcpSession) {
        return null;
    }

    @Override
    public MrcpResponse stop(StopRequest stopRequest, MrcpSession mrcpSession) {
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

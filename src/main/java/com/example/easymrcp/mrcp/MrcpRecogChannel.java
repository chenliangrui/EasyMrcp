package com.example.easymrcp.mrcp;

import com.example.easymrcp.asr.AsrHandler;
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

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
public class MrcpRecogChannel implements RecogOnlyRequestHandler {
    private AsrHandler asrHandler;

    public MrcpRecogChannel(AsrHandler rtp) {
        this.asrHandler = rtp;
    }

    @Override
    public MrcpResponse defineGrammar(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        log.info("MrcpRecogChannel.defineGrammar");
        MrcpResponse response = mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.COMPLETE);
        return response;
    }

    @Override
    public MrcpResponse recognize(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        // 设置语音识别完成的回调，当asr完成识别后调用回调
        Callback callback = new Callback() {
            @Override
            public void apply(String msg) {
                // 语音识别完成
                try {
                    MrcpEvent eventComplete = mrcpSession.createEvent(MrcpEventName.RECOGNITION_COMPLETE, MrcpRequestState.COMPLETE);
                    CompletionCause completionCause = new CompletionCause((short) 0, "success");
                    eventComplete.addHeader(MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause));
                    if (!msg.isEmpty()) {
                        eventComplete.setContent("text/plain", null, msg);
                        mrcpSession.postEvent(eventComplete);
                    }
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        asrHandler.setCallback(callback);
        log.info("MrcpRecogChannel recognize");
        MrcpResponse response = mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.IN_PROGRESS);
        //TODO 开始发送语音，时机需要考虑
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    //TODO 发送后IN_PROGRESS会打断语音合成
//                    Thread.sleep(10000);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//                MrcpEvent event = mrcpSession.createEvent(MrcpEventName.START_OF_INPUT, MrcpRequestState.IN_PROGRESS);
//                try {
//                    mrcpSession.postEvent(event);
//                } catch (TimeoutException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }).start();
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
        MrcpResponse response = null;
        response = mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.COMPLETE);
        return response;
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

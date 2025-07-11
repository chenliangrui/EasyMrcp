package com.cfsl.easymrcp.mrcp;

import com.cfsl.easymrcp.asr.AsrHandler;
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

import java.util.concurrent.TimeoutException;

/**
 * Mrcp协议中asr处理
 */
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
        AsrCallback callback = new AsrCallback() {
            @Override
            public void apply(String msg) {
                if (!mrcpSession.isComplete()) {
                    // 发送后IN_PROGRESS会打断tts，添加在此处是当完成asr识别时会打断当前正在播放的tts
                    MrcpEvent event = mrcpSession.createEvent(MrcpEventName.START_OF_INPUT, MrcpRequestState.IN_PROGRESS);
                    try {
                        mrcpSession.postEvent(event);
                    } catch (TimeoutException e) {
                        log.error("postEvent START_OF_INPUT error", e);
                    }
                    // 发送识别完成事件
                    try {
                        MrcpEvent eventComplete = mrcpSession.createEvent(MrcpEventName.RECOGNITION_COMPLETE, MrcpRequestState.COMPLETE);
                        CompletionCause completionCause = new CompletionCause((short) 0, "success");
                        eventComplete.addHeader(MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause));
                        if (!msg.isEmpty()) {
                            eventComplete.setContent("text/plain", null, msg);
                            mrcpSession.postEvent(eventComplete);
                        }
                    } catch (TimeoutException e) {
                        log.error("postEvent RECOGNITION_COMPLETE error", e);
                    }
                }
            }
        };
        asrHandler.setCallback(callback);
        log.info("MrcpRecogChannel recognize");
        MrcpResponse response = mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.IN_PROGRESS);
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

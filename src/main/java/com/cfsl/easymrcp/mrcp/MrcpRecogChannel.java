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
        // 解析超时参数
        Long speechCompleteTimeout = getHeaderValueAsLong(unimplementedRequest, MrcpHeaderName.SPEECH_COMPLETE_TIMEOUT.toString());
        // 实际应用中不太有用，已注释但仍解析参数
        Long speechIncompleteTimeout = getHeaderValueAsLong(unimplementedRequest, MrcpHeaderName.SPEECH_INCOMPLETE_TIMEOUT.toString());
        Long noInputTimeout = getHeaderValueAsLong(unimplementedRequest, MrcpHeaderName.NO_INPUT_TIMEOUT.toString());
        // 实际应用中不太有用，已注释但仍解析参数
        Long recognitionTimeout = getHeaderValueAsLong(unimplementedRequest, MrcpHeaderName.RECOGNITION_TIMEOUT.toString());
        Boolean startInputTimers = getHeaderValueAsBoolean(unimplementedRequest, MrcpHeaderName.START_INPUT_TIMERS.toString());
        
        log.info("RECOGNIZE params: speech-complete-timeout={}, speech-incomplete-timeout={}, no-input-timeout={}, recognition-timeout={}, start-input-timers={}",
                speechCompleteTimeout, speechIncompleteTimeout, noInputTimeout, recognitionTimeout, startInputTimers);
        
        // 直接设置Speech-Complete-Timeout参数到AsrHandler，用于VAD初始化
        if (speechCompleteTimeout != null && speechCompleteTimeout > 0) {
            asrHandler.setSpeechCompleteTimeout(speechCompleteTimeout);
            log.info("Setting Speech-Complete-Timeout ({} ms) for VAD initialization", speechCompleteTimeout);
        }
        
        // 创建超时回调
        MrcpTimeoutManager.TimeoutCallback timeoutCallback = new MrcpTimeoutManager.TimeoutCallback() {
            @Override
            public void onNoInputTimeout() {
                sendTimeoutEvent(mrcpSession, (short) 3, "no-input-timeout");
            }

            @Override
            public void onSpeechCompleteTimeout() {
                // VAD检测到语音结束后会触发此回调
                // 在这里无需调用asrHandler.sendEof()，因为VAD已经在检测到语音结束时调用了
                // 此处主要负责发送RECOGNITION_COMPLETE事件
                log.info("Speech Complete Timeout triggered, VAD has detected end of speech");
            }

            @Override
            public void onSpeechIncompleteTimeout() {
                // 实际应用中不太有用，已注释
                // sendTimeoutEvent(mrcpSession, (short) 7, "speech-incomplete-timeout");
            }

            @Override
            public void onRecognitionTimeout() {
                // 实际应用中不太有用，已注释
                // sendTimeoutEvent(mrcpSession, (short) 4, "recognition-timeout");
            }
        };
        
        // 创建超时管理器并设置超时参数
        MrcpTimeoutManager timeoutManager = new MrcpTimeoutManager(timeoutCallback);
        timeoutManager.setSpeechCompleteTimeout(speechCompleteTimeout);
        timeoutManager.setSpeechIncompleteTimeout(speechIncompleteTimeout);
        timeoutManager.setNoInputTimeout(noInputTimeout);
        timeoutManager.setRecognitionTimeout(recognitionTimeout);
        timeoutManager.setStartInputTimers(startInputTimers);
        
        // 将超时管理器传递给AsrHandler
        asrHandler.setTimeoutManager(timeoutManager);
        
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
                        
                        // 完成时取消所有计时器
                        asrHandler.cancelTimeouts();
                    } catch (TimeoutException e) {
                        log.error("postEvent RECOGNITION_COMPLETE error", e);
                    }
                }
            }
        };
        asrHandler.setCallback(callback);
        
        // 启动超时计时
        timeoutManager.startTimers();
        
        log.info("MrcpRecogChannel recognize");
        MrcpResponse response = mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.IN_PROGRESS);
        return response;
    }
    
    private void sendTimeoutEvent(MrcpSession mrcpSession, short causeCode, String causeName) {
        if (!mrcpSession.isComplete()) {
            try {
                MrcpEvent eventComplete = mrcpSession.createEvent(MrcpEventName.RECOGNITION_COMPLETE, MrcpRequestState.COMPLETE);
                CompletionCause completionCause = new CompletionCause(causeCode, causeName);
                eventComplete.addHeader(MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause));
                mrcpSession.postEvent(eventComplete);
                
                // 取消所有计时器
                asrHandler.cancelTimeouts();
            } catch (TimeoutException e) {
                log.error("postEvent timeout event error", e);
            }
        }
    }
    
    private Long getHeaderValueAsLong(MrcpRequestFactory.UnimplementedRequest request, String headerName) {
        String value = request.getHeader(headerName) != null ? request.getHeader(headerName).getValueString() : null;
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse header value as Long: {}", value);
            }
        }
        return null;
    }
    
    private Boolean getHeaderValueAsBoolean(MrcpRequestFactory.UnimplementedRequest request, String headerName) {
        String value = request.getHeader(headerName) != null ? request.getHeader(headerName).getValueString() : null;
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return null;
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
        // 响应START_INPUT_TIMERS请求，启动计时器
        asrHandler.startInputTimers();
        return mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.COMPLETE);
    }

    @Override
    public MrcpResponse stop(StopRequest stopRequest, MrcpSession mrcpSession) {
        // 取消所有超时定时器
        asrHandler.cancelTimeouts();
        MrcpResponse response = mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.COMPLETE);
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

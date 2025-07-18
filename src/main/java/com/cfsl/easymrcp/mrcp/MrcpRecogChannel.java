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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Mrcp协议中asr处理
 */
@Slf4j
public class MrcpRecogChannel implements RecogOnlyRequestHandler {
    private AsrHandler asrHandler;
    private MrcpManage mrcpManage;

    public MrcpRecogChannel(AsrHandler rtp, MrcpManage mrcpManage) {
        this.asrHandler = rtp;
        this.mrcpManage = mrcpManage;
    }

    @Override
    public MrcpResponse defineGrammar(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        log.info("MrcpRecogChannel.defineGrammar");
        MrcpResponse response = mrcpSession.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.COMPLETE);
        return response;
    }

    @Override
    public MrcpResponse recognize(MrcpRequestFactory.UnimplementedRequest unimplementedRequest, MrcpSession mrcpSession) {
        String content = unimplementedRequest.getContent();
        log.info("Recognize content: {}", content);
        // 解析content中的参数
        Map<String, String> params = parseContentParams(content);
        String callId = params.get("call-id");
        if (callId == null) {
            log.error("call-id is null, cannot get call-id");
        }
        asrHandler.setCallId(callId);
        // 从解析的参数中获取值，使用MrcpHeaderName枚举作为字段名称
        Boolean startInputTimers = Boolean.parseBoolean(params.getOrDefault(MrcpHeaderName.START_INPUT_TIMERS.toString().toLowerCase(), "false"));
        Long noInputTimeout = parseLong(params.get(MrcpHeaderName.NO_INPUT_TIMEOUT.toString().toLowerCase()));
        // speech-timeout 映射为 speech-complete-timeout
        Long speechCompleteTimeout = parseLong(params.get("speech-timeout"));
        if (speechCompleteTimeout == null) {
            // 尝试直接使用标准枚举名称
            speechCompleteTimeout = parseLong(params.get(MrcpHeaderName.SPEECH_COMPLETE_TIMEOUT.toString().toLowerCase()));
        }
        Boolean autoResume = Boolean.parseBoolean(params.getOrDefault("auto-resume", "false"));
        String killOnBargeInString = params.get(MrcpHeaderName.KILL_ON_BARGE_IN.toString().toLowerCase());
        Boolean killOnBargeIn = null;
        if (killOnBargeInString != null) {
            killOnBargeIn = Boolean.parseBoolean(killOnBargeInString);
        }

        // 保留原有的其他参数解析，但默认为null
        Long speechIncompleteTimeout = null;
        Long recognitionTimeout = null;

        log.info("RECOGNIZE params: speech-complete-timeout={}, speech-incomplete-timeout={}, no-input-timeout={}, recognition-timeout={}," +
                        " start-input-timers={}, auto-resume={}, kill-on-barge-in={}",
                speechCompleteTimeout, speechIncompleteTimeout, noInputTimeout, recognitionTimeout, startInputTimers, autoResume, killOnBargeIn);

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
        };

        // 创建超时管理器并设置超时参数
        MrcpTimeoutManager timeoutManager = new MrcpTimeoutManager(timeoutCallback);
//        timeoutManager.setSpeechCompleteTimeout(speechCompleteTimeout);
//        timeoutManager.setSpeechIncompleteTimeout(speechIncompleteTimeout);
        timeoutManager.setNoInputTimeout(noInputTimeout);
        timeoutManager.setRecognitionTimeout(recognitionTimeout);
        timeoutManager.setStartInputTimers(startInputTimers);

        // 将超时管理器传递给AsrHandler
        asrHandler.setTimeoutManager(timeoutManager);

        // 设置语音识别完成的回调，当asr完成识别后调用回调
        AsrCallback callback = new AsrCallback() {
            @Override
            public void apply(String action, String msg) {
                if (!mrcpSession.isComplete()) {
                    // 发送后IN_PROGRESS会打断tts，添加在此处是当完成asr识别时会打断当前正在播放的tts
//                    MrcpEvent event = mrcpSession.createEvent(MrcpEventName.START_OF_INPUT, MrcpRequestState.IN_PROGRESS);
//                    try {
//                        mrcpSession.postEvent(event);
//                    } catch (TimeoutException e) {
//                        log.error("postEvent START_OF_INPUT error", e);
//                    }
                    mrcpManage.interrupt(callId);
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

    /**
     * 解析content字符串中的参数
     * 格式为: session:{param1=value1,param2=value2,...}
     *
     * @param content 内容字符串
     * @return 解析后的参数映射
     */
    private Map<String, String> parseContentParams(String content) {
        Map<String, String> params = new HashMap<>();
        if (content == null || content.isEmpty()) {
            return params;
        }

        try {
            // 提取session:{...}中的内容
            int startIndex = content.indexOf("{");
            int endIndex = content.lastIndexOf("}");

            if (startIndex >= 0 && endIndex > startIndex) {
                String paramString = content.substring(startIndex + 1, endIndex);
                String[] pairs = paramString.split(",");

                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().toLowerCase(); // 统一转为小写以便于匹配
                        String value = keyValue[1].trim();
                        params.put(key, value);
                    }
                }
            }

            // 记录所有解析出的参数，便于调试
            if (!params.isEmpty()) {
                log.debug("Parsed parameters from content: {}", params);
            }
        } catch (Exception e) {
            log.error("Failed to parse content parameters: {}", content, e);
        }

        return params;
    }

    /**
     * 解析字符串为Long，失败返回null
     */
    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse value as Long: {}", value);
            return null;
        }
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

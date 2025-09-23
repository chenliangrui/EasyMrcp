package com.cfsl.easymrcp.tcp.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.mrcp.MrcpTimeoutManager;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.MrcpEventHandler;
import com.cfsl.easymrcp.tcp.MrcpEvent;
import com.cfsl.easymrcp.tcp.TcpEventType;
import com.cfsl.easymrcp.tcp.TcpResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DetectSpeechEventHandler implements MrcpEventHandler {
    MrcpManage mrcpManage;

    public DetectSpeechEventHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleEvent(MrcpEvent event, TcpClientNotifier tcpClientNotifier) {
        String id = event.getId();
        AsrHandler asrHandler = mrcpManage.getAsrHandler(id);
        if (asrHandler == null) {
            return null;
        }
        // 创建超时回调
        MrcpTimeoutManager.TimeoutCallback timeoutCallback = new MrcpTimeoutManager.TimeoutCallback() {
            @Override
            public void onNoInputTimeout() {
                tcpClientNotifier.sendEvent(id, null,TcpEventType.NoInputTimeout, "no-input-timeout");
                // 取消所有计时器
                asrHandler.cancelTimeouts();
                asrHandler.startInputTimers();
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
        JSONObject asrParams = JSON.parseObject(event.getData());
        if (asrParams != null) {
            if (asrParams.getBoolean(ASRConstant.StartInputTimers)) {
                timeoutManager.setStartInputTimers(true);
                Long noInputTimeout = asrParams.getLong(ASRConstant.NoInputTimeout);
                if (noInputTimeout != null && noInputTimeout > 0) {
                    timeoutManager.setNoInputTimeout(noInputTimeout);
                }
                Long speechCompleteTimeout = asrParams.getLong(ASRConstant.SpeechCompleteTimeout);
                if (speechCompleteTimeout != null && speechCompleteTimeout > 0) {
//                    asrHandler.setSpeechCompleteTimeout(speechCompleteTimeout);
                    log.info("Setting Speech-Complete-Timeout ({} ms) for VAD initialization", speechCompleteTimeout);
                }
                Boolean automaticInterruption = asrParams.getBoolean(ASRConstant.AutomaticInterruption);
                if (automaticInterruption != null) {
                    asrHandler.setAutomaticInterruption(automaticInterruption);
                }
            } else {
                // 关闭超时计时器
                timeoutManager.setStartInputTimers(false);
            }
        }

        // 将超时管理器传递给AsrHandler
        asrHandler.setTimeoutManager(timeoutManager);

        asrHandler.setCallback(new AsrCallback() {
            @Override
            public void apply(String action, String msg) {
                if (asrHandler.getAutomaticInterruption() && action.equals(ASRConstant.Interrupt)) {
                    mrcpManage.clearAllSpeakTaskAndInterrupt(id);
                    asrHandler.cancelTimeouts();
                }
                if (action.equals(ASRConstant.Result)) {
                    if (!msg.isEmpty()) {
                        tcpClientNotifier.sendEvent(id, null, TcpEventType.RecognitionComplete, msg);
                    }
                    asrHandler.startInputTimers();
                }
            }
        });

        // 启动超时计时
        timeoutManager.startTimers();
        return TcpResponse.success(id, "success");
    }
}

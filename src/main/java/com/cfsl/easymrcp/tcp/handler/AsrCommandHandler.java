package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.mrcp.MrcpTimeoutManager;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpCommandHandler;
import com.cfsl.easymrcp.tcp.TcpEvent;
import com.cfsl.easymrcp.tcp.TcpEventType;
import com.cfsl.easymrcp.tcp.TcpResponse;
import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpEventName;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.header.CompletionCause;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.server.MrcpSession;

import java.util.concurrent.TimeoutException;

@Slf4j
public class AsrCommandHandler implements TcpCommandHandler {
    MrcpManage mrcpManage;

    public AsrCommandHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleEvent(TcpEvent event, TcpClientNotifier tcpClientNotifier) {
        String id = event.getId();
        AsrHandler asrHandler = mrcpManage.getAsrHandler(id);
        Long speechCompleteTimeout = 800l;

        // 直接设置Speech-Complete-Timeout参数到AsrHandler，用于VAD初始化
        if (speechCompleteTimeout != null && speechCompleteTimeout > 0) {
            asrHandler.setSpeechCompleteTimeout(speechCompleteTimeout);
            log.info("Setting Speech-Complete-Timeout ({} ms) for VAD initialization", speechCompleteTimeout);
        }

        // 创建超时回调
        MrcpTimeoutManager.TimeoutCallback timeoutCallback = new MrcpTimeoutManager.TimeoutCallback() {
            @Override
            public void onNoInputTimeout() {
                tcpClientNotifier.sendEvent(id, TcpEventType.NoInputTimeout, "no-input-timeout");
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
        timeoutManager.setNoInputTimeout(15000L);
        timeoutManager.setStartInputTimers(true);

        // 将超时管理器传递给AsrHandler
        asrHandler.setTimeoutManager(timeoutManager);

        asrHandler.setCallback(new AsrCallback() {
            @Override
            public void apply(String msg) {
                // TODO 放到vad和实时语音识别中进行处理，并且重置定时器
                mrcpManage.interrupt(id);
                tcpClientNotifier.sendEvent(id, TcpEventType.RecognitionComplete, msg);
            }
        });

        // 启动超时计时
        timeoutManager.startTimers();
        return null;
    }
}

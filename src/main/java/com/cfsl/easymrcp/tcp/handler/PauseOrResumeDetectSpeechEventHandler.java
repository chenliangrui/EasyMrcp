package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.*;

/**
 * 暂停或者恢复ASR识别
 * 可以用于某些偶尔不需要asr的场景，可以PauseDetectSpeech暂停识别并在需要的时候ResumeDetectSpeech恢复识别。
 * 该参数将同时重置NoInputTimeout事件的时间，暂停时不会触发NoInputTimeout，恢复后即可正常进行NoInputTimeout
 */
public class PauseOrResumeDetectSpeechEventHandler implements MrcpEventHandler {
    MrcpManage mrcpManage;

    public PauseOrResumeDetectSpeechEventHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleEvent(MrcpEvent event, TcpClientNotifier tcpClientNotifier) {
        String id = event.getId();
        AsrHandler asrHandler = mrcpManage.getAsrHandler(id);
        if (event.getEvent().equals(TcpEventType.PauseDetectSpeech.toString())) {
            // 暂停ASR识别
            asrHandler.cancelTimeouts();
            asrHandler.pauseAsr();
        } else if (event.getEvent().equals(TcpEventType.ResumeDetectSpeech.toString())) {
            // 恢复ASR识别
            asrHandler.startInputTimers();
            asrHandler.resumeAsr();
        }
        return TcpResponse.success(id, "success");
    }
}

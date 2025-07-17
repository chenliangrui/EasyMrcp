package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.MrcpEvent;
import com.cfsl.easymrcp.tcp.MrcpEventHandler;
import com.cfsl.easymrcp.tcp.TcpEventType;
import com.cfsl.easymrcp.tcp.TcpResponse;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpeakEventHandler implements MrcpEventHandler {
    MrcpManage mrcpManage;

    public SpeakEventHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleEvent(MrcpEvent event, TcpClientNotifier tcpClientNotifier) {
        String id = event.getId();
        AsrHandler asrHandler = mrcpManage.getAsrHandler(id);
        asrHandler.cancelTimeouts();
        TtsHandler ttsHandler = mrcpManage.getTtsHandler(id);
        log.info("tts开始，设置true");
        mrcpManage.setSpeaking(id,true);
        ttsHandler.setCallback(new TtsCallback() {
            @Override
            public void apply(String msg) {
                if (msg.equals("completed")) {
                    tcpClientNotifier.sendEvent(id, TcpEventType.SpeakComplete, msg);
                    asrHandler.startInputTimers();
                    mrcpManage.setSpeaking(id,false);
                    log.info("tts结束，设置false");
                } else {
                    tcpClientNotifier.sendEvent(id, TcpEventType.SpeakInterrupted, msg);
                }
            }
        });
        ttsHandler.transmit(event.getData());
        log.info("speak event received");
        return null;
    }
}

package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.tcp.*;
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
        if (asrHandler == null) {
            return null;
        }
        asrHandler.cancelTimeouts();
        TtsHandler ttsHandler = mrcpManage.getTtsHandler(id);
        if (ttsHandler == null) {
            return null;
        }
        if (event.getEvent().equals(TcpEventType.InterruptAndSpeak.name())) {
            // 清除speak队列中未完成的任务
            mrcpManage.clearAllSpeakTask(id);
            // 中断当前TTS
            mrcpManage.interrupt(id);
        }
        MrcpEventWithCallback mrcpEventWithCallback = new MrcpEventWithCallback();
        mrcpEventWithCallback.setRunnable(new Runnable() {
            @Override
            public void run() {
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
                            // 继续speak
                            mrcpManage.runNextSpeak(id);
                        } else {
                            tcpClientNotifier.sendEvent(id, TcpEventType.SpeakInterrupted, msg);
                        }
                    }
                });
                if (event.getEvent().equals(TcpEventType.Silence.name())) {
                    ttsHandler.silence(Integer.parseInt(event.getData()));
                } else {
                    ttsHandler.transmit(event.getData());
                }
                log.info("speak event received");
            }
        });
        mrcpManage.addEvent(id, mrcpEventWithCallback);
        return null;
    }
}

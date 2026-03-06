package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.tcp.*;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

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
            mrcpManage.clearAllSpeakTaskAndInterrupt(id);
        }
        MrcpEventWithCallback mrcpEventWithCallback = new MrcpEventWithCallback(event.getEvent());
        String ttsEngineId = mrcpEventWithCallback.getId();
        mrcpEventWithCallback.setConsumer(new Consumer<String>() {
            @Override
            public void accept(String pre /* 处理状态 */) {
                if (pre.equals("normal")) {
                    if (event.getEvent().equals(TcpEventType.SpeakWithNoInterrupt.name())) {
                        mrcpManage.setInterruptEnable(id, false);
                    }
                }
                mrcpManage.setSpeaking(id,true);
                ttsHandler.addCallback(ttsEngineId, new TtsCallback() {
                    @Override
                    public void apply(String msg) {
                        ttsHandler.removeCallback(ttsEngineId);
                        ttsHandler.getTtsProcessor().removeTtsEngine(ttsEngineId);
                        if (msg.equals("completed")) {
                            if (!mrcpManage.isInterruptEnable(id)) mrcpManage.setInterruptEnable(id, true);
                            tcpClientNotifier.sendEvent(id, event.getEventId(), TcpEventType.SpeakComplete, msg);
                            asrHandler.startInputTimers();
                            mrcpManage.setSpeaking(id,false);
                            // 继续speak
                            mrcpManage.runNextSpeak(id);
                        } else {
                            tcpClientNotifier.sendEvent(id, event.getEventId(), TcpEventType.SpeakInterrupted, msg);
                        }
                    }
                });
                if (event.getEvent().equals(TcpEventType.Silence.name())) {
                    ttsHandler.silence(Integer.parseInt(event.getData()), ttsEngineId);
                } else {
                    ttsHandler.transmit(id, event.getData(), pre, ttsEngineId);
                }
            }
        });
        mrcpManage.addEvent(id, mrcpEventWithCallback);
        return TcpResponse.success(id, "success");
    }
}

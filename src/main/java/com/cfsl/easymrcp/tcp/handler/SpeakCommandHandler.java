package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpCommand;
import com.cfsl.easymrcp.tcp.TcpCommandHandler;
import com.cfsl.easymrcp.tcp.TcpResponse;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpeakCommandHandler implements TcpCommandHandler {
    MrcpManage mrcpManage;

    public SpeakCommandHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleCommand(TcpCommand command, TcpClientNotifier tcpClientNotifier) {
        String id = command.getId();
        AsrHandler asrHandler = mrcpManage.getAsrHandler(id);
        asrHandler.cancelTimeouts();
        TtsHandler ttsHandler = mrcpManage.getTtsHandler(command.getId());
        ttsHandler.setCallback(new TtsCallback() {
            @Override
            public void apply(String msg) {
                if (msg.equals("completed")) {
                    tcpClientNotifier.sendAsrResultNotify(id, "SpeakComplete", msg);
                    asrHandler.startInputTimers();
                } else {
                    tcpClientNotifier.sendAsrResultNotify(id, "interrupt", msg);
                }
            }
        });
        ttsHandler.transmit(command.getData().toString());
        log.info("speak command received");
        return null;
    }
}

package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.TcpCommand;
import com.cfsl.easymrcp.tcp.TcpCommandHandler;
import com.cfsl.easymrcp.tcp.TcpResponse;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InterruptAndSpeakCommandHandler implements TcpCommandHandler {
    MrcpManage mrcpManage;

    public InterruptAndSpeakCommandHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleCommand(TcpCommand command) {
        TtsHandler ttsHandler = mrcpManage.getTtsHandler(command.getId());
        mrcpManage.interrupt(command.getId());
        ttsHandler.transmit(command.getData().toString());
        log.info("speak command received");
        return null;
    }
}

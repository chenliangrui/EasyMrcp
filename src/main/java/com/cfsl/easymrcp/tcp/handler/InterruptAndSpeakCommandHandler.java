package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpEvent;
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
    public TcpResponse handleEvent(TcpEvent event, TcpClientNotifier tcpClientNotifier) {
        String id = event.getId();
        TtsHandler ttsHandler = mrcpManage.getTtsHandler(id);
        
        // 中断当前TTS
        mrcpManage.interrupt(id);
        
        // 执行新的TTS合成
        ttsHandler.transmit(event.getData());
        log.info("interrupt and speak event received");
        return null;
    }
}

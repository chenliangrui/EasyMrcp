package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.*;

public class InterruptEventHandler implements MrcpEventHandler {
    MrcpManage mrcpManage;

    public InterruptEventHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleEvent(MrcpEvent event, TcpClientNotifier tcpClientNotifier) {
        String id = event.getId();
        mrcpManage.clearAllSpeakTaskAndInterrupt(id);
        tcpClientNotifier.sendEvent(id, TcpEventType.SpeakInterrupted, "interrupt");
        return TcpResponse.success(id, "success");
    }
}

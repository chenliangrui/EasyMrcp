package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.MrcpEvent;
import com.cfsl.easymrcp.tcp.MrcpEventHandler;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientConnectEventHandler implements MrcpEventHandler {
    MrcpManage mrcpManage;

    public ClientConnectEventHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleEvent(MrcpEvent event, TcpClientNotifier tcpClientNotifier) {
        String id = event.getId();
        mrcpManage.updateConnection(id);
        return TcpResponse.success(id, "success");
    }
}

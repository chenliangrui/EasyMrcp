package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpCommandHandler;
import com.cfsl.easymrcp.tcp.TcpEvent;
import com.cfsl.easymrcp.tcp.TcpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Echo命令处理器
 */
public class EchoCommandHandler implements TcpCommandHandler {

    private MrcpManage mrcpManage;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EchoCommandHandler.class);
    
    private static final String COMMAND_TYPE = "echo";

    public EchoCommandHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleEvent(TcpEvent event, TcpClientNotifier tcpClientNotifier) {
        LOGGER.info("处理Echo命令: {}", event);
        if (mrcpManage != null) {
            mrcpManage.setSpeaking(event.getId(), true);
        }
        return TcpResponse.success(event.getId(), event.getData());
    }
}
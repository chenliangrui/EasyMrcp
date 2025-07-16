package com.cfsl.easymrcp.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认TCP命令处理器
 */
public class DefaultTcpCommandHandler implements TcpCommandHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTcpCommandHandler.class);
    
    private static final String COMMAND_TYPE = "default";
    
    @Override
    public TcpResponse handleCommand(TcpCommand command, TcpClientNotifier tcpClientNotifier) {
        LOGGER.info("处理默认命令: {}", command);
        return TcpResponse.success(command.getId(), "Echo from server: " + command.toString());
    }
} 
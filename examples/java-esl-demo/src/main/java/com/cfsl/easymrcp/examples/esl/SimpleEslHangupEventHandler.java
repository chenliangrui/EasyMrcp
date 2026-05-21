package com.cfsl.easymrcp.examples.esl;

import link.thingscloud.freeswitch.esl.constant.EventNames;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 监听 `CHANNEL_HANGUP` 事件的 demo 处理器。
 *
 * 它和 `SimpleEslCallListener` 配套使用：
 * park 时建会话，hangup 时按同一个 UUID 把会话关掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EslEventName(EventNames.CHANNEL_HANGUP)
public class SimpleEslHangupEventHandler implements EslEventHandler {

    private final SimpleEslCallListener callListener;

    @Override
    public void handle(String addr, EslEvent event) {
        String uuid = event.getEventHeaders().get("Unique-ID");
        if (uuid == null || uuid.isEmpty()) {
            log.warn("CHANNEL_HANGUP事件缺少Unique-ID");
            return;
        }

        callListener.closeSession(uuid);
    }
}

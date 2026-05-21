package com.cfsl.easymrcp.examples.esl;

import com.cfsl.easymrcp.examples.esl.config.EasyMrcpDemoProperties;
import io.netty.channel.EventLoopGroup;
import link.thingscloud.freeswitch.esl.constant.EventNames;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听 `CHANNEL_PARK` 事件的 demo 处理器。
 *
 * 在这个示例里，`CHANNEL_PARK` 被当作“通话已进入可接管阶段”的触发点：
 * 一旦拿到该事件里的 `Unique-ID`，就为这通电话创建一个独立的 EasyMrcp 会话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EslEventName(EventNames.CHANNEL_PARK)
public class SimpleEslCallListener implements EslEventHandler {

    private final EasyMrcpDemoProperties properties;
    private final EventLoopGroup easyMrcpEventLoopGroup;
    /**
     * 按通话 UUID 保存当前活跃的 EasyMrcp 会话。
     *
     * 这样做的目的，是让每通电话都绑定自己的 handler，
     * 后续收到 hangup 时可以按 UUID 精确回收。
     */
    private final Map<String, SimpleEslEasyMrcpHandler> sessions = new ConcurrentHashMap<>();

    @Override
    public void handle(String addr, EslEvent event) {
        String uuid = event.getEventHeaders().get("Unique-ID");
        if (uuid == null || uuid.isEmpty()) {
            log.warn("CHANNEL_PARK事件缺少Unique-ID");
            return;
        }

        sessions.computeIfAbsent(uuid, key -> {
            log.info("创建EasyMrcp会话, uuid={}", key);
            SimpleEslEasyMrcpHandler handler = new SimpleEslEasyMrcpHandler(
                    key,
                    properties,
                    easyMrcpEventLoopGroup
            );
            handler.start();
            return handler;
        });
    }

    /** 按 UUID 关闭某一通电话对应的 EasyMrcp 会话。 */
    public void closeSession(String uuid) {
        SimpleEslEasyMrcpHandler handler = sessions.remove(uuid);
        if (handler != null) {
            log.info("关闭EasyMrcp会话, uuid={}", uuid);
            handler.close();
        }
    }

    /** 应用退出时统一回收所有还没关闭的会话。 */
    @PreDestroy
    public void destroy() {
        sessions.values().forEach(SimpleEslEasyMrcpHandler::close);
        sessions.clear();
    }
}

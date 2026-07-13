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
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    static final String BRIDGE_LEG_VARIABLE = "variable_easymrcp_bridge_leg";

    private final EasyMrcpDemoProperties properties;
    private final EventLoopGroup easyMrcpEventLoopGroup;
    private final FreeSwitchCallController callController;
    private final Object sessionLock = new Object();
    private final Set<SimpleEslEasyMrcpHandler> initializingSessions =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<SimpleEslEasyMrcpHandler> deferredClosures =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * 按通话 UUID 保存当前活跃的 EasyMrcp 会话。
     *
     * 这样做的目的，是让每通电话都绑定自己的 handler，
     * 后续收到 hangup 时可以按 UUID 精确回收。
     */
    private final Map<String, SimpleEslEasyMrcpHandler> sessions = new ConcurrentHashMap<>();

    @Override
    public void handle(String addr, EslEvent event) {
        // Controller 创建的 B-leg 也会触发事件，必须忽略以避免递归创建会话。
        if (isBridgeLeg(event)) {
            return;
        }

        String uuid = event.getEventHeaders().get("Unique-ID");
        if (uuid == null || uuid.isEmpty()) {
            log.warn("CHANNEL_PARK事件缺少Unique-ID");
            return;
        }

        SimpleEslEasyMrcpHandler handler;
        RuntimeException runtimeFailure = null;
        Error errorFailure = null;
        boolean shouldClose = false;

        synchronized (sessionLock) {
            if (sessions.containsKey(uuid)) {
                return;
            }

            handler = createHandler(uuid);
            sessions.put(uuid, handler);
            initializingSessions.add(handler);
            try {
                log.info("创建EasyMrcp会话, uuid={}", uuid);
                handler.start();
                if (sessions.get(uuid) == handler) {
                    callController.answerAndBridge(addr, uuid, command -> {
                        log.error("FreeSWITCH call control failed, uuid={}, command={}", uuid, command);
                        closeSession(uuid, handler);
                    });
                }
            } catch (RuntimeException e) {
                runtimeFailure = e;
                shouldClose = sessions.remove(uuid, handler);
            } catch (Error e) {
                errorFailure = e;
                shouldClose = sessions.remove(uuid, handler);
            } finally {
                initializingSessions.remove(handler);
                shouldClose |= deferredClosures.remove(handler);
            }
        }

        if (shouldClose) {
            try {
                closeHandler(uuid, handler);
            } catch (RuntimeException | Error closeFailure) {
                if (runtimeFailure != null) {
                    runtimeFailure.addSuppressed(closeFailure);
                } else if (errorFailure != null) {
                    errorFailure.addSuppressed(closeFailure);
                } else {
                    throw closeFailure;
                }
            }
        }
        if (runtimeFailure != null) {
            throw runtimeFailure;
        }
        if (errorFailure != null) {
            throw errorFailure;
        }
    }

    SimpleEslEasyMrcpHandler createHandler(String uuid) {
        return new SimpleEslEasyMrcpHandler(
                uuid,
                properties,
                easyMrcpEventLoopGroup
        );
    }

    /** 按 UUID 关闭某一通电话对应的 EasyMrcp 会话。 */
    public void closeSession(String uuid) {
        closeSession(uuid, null);
    }

    private void closeSession(String uuid, SimpleEslEasyMrcpHandler expectedHandler) {
        SimpleEslEasyMrcpHandler handler;
        synchronized (sessionLock) {
            if (expectedHandler == null) {
                handler = sessions.remove(uuid);
            } else if (sessions.remove(uuid, expectedHandler)) {
                handler = expectedHandler;
            } else {
                return;
            }
            if (handler == null) {
                // B-leg 或重复的 hangup 可能已无会话，直接返回即可。
                return;
            }
            if (initializingSessions.contains(handler)) {
                deferredClosures.add(handler);
                return;
            }
        }

        closeHandler(uuid, handler);
    }

    static boolean isBridgeLeg(EslEvent event) {
        return "true".equalsIgnoreCase(event.getEventHeaders().get(BRIDGE_LEG_VARIABLE));
    }

    private void closeHandler(String uuid, SimpleEslEasyMrcpHandler handler) {
        log.info("关闭EasyMrcp会话, uuid={}", uuid);
        handler.close();
    }

    /** 应用退出时统一回收所有还没关闭的会话。 */
    @PreDestroy
    public void destroy() {
        List<SimpleEslEasyMrcpHandler> handlers;
        synchronized (sessionLock) {
            handlers = new ArrayList<>(sessions.values());
            sessions.clear();
        }
        handlers.forEach(SimpleEslEasyMrcpHandler::close);
    }
}

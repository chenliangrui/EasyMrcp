package com.cfsl.easymrcp.examples.esl;

import link.thingscloud.freeswitch.esl.InboundClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动期探针。
 *
 * 这个类的目的不是处理业务，而是尽早判断 ESL starter 是否真的生效。
 * 如果 `InboundClient` bean 都没有创建出来，就说明当前 demo 只是 Spring Boot 启动了，
 * 但 ESL 链路并没有真正接上，此时直接失败比“假成功”更容易排查问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EslStartupVerifier implements ApplicationRunner {

    /** 由 ESL starter 提供的核心客户端 bean。 */
    private final InboundClient inboundClient;

    @Override
    public void run(ApplicationArguments args) {
        if (inboundClient == null) {
            throw new IllegalStateException("InboundClient bean 未创建，ESL starter 未正确生效");
        }
        log.info("ESL starter 已加载，InboundClient bean 就绪: {}", inboundClient.getClass().getName());
    }
}

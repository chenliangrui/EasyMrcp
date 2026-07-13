package com.cfsl.easymrcp.examples.esl;

import com.cfsl.easymrcp.examples.esl.config.EasyMrcpDemoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * ESL demo 的 Spring Boot 入口。
 *
 * 这里除了扫描 demo 自己的包，还显式扫描 `link.thingscloud.freeswitch.esl`，
 * 目的是确保 starter 里的自动配置和相关组件能够被 Spring 正确发现。
 */
@SpringBootApplication
@ComponentScan({"com.cfsl.easymrcp.examples.esl", "link.thingscloud.freeswitch.esl"})
@EnableConfigurationProperties(EasyMrcpDemoProperties.class)
public class EslEasyMrcpDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EslEasyMrcpDemoApplication.class, args);
    }
}

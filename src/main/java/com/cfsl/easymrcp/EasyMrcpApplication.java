package com.cfsl.easymrcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EasyMrcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyMrcpApplication.class, args);
    }

}

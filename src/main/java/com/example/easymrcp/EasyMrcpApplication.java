package com.example.easymrcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;

@SpringBootApplication
public class EasyMrcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyMrcpApplication.class, args);
    }

}

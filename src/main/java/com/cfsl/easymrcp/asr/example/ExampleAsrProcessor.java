package com.cfsl.easymrcp.asr.example;

import com.cfsl.easymrcp.asr.AsrHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExampleAsrProcessor extends AsrHandler {
    private boolean start;
    private ExampleAsrConfig exampleConfig;

    public ExampleAsrProcessor(ExampleAsrConfig exampleConfig) {
        this.exampleConfig = exampleConfig;
    }

    @Override
    public void create() {
        // 必须执行，此时asr创建成功，并且开始识别
        countDownLatch.countDown();
    }

    @Override
    public void receive(byte[] pcmData) {
        if (!start) {
            start = true;
            // 连接
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(3000);
                        getCallback().apply("识别成功");
                    } catch (InterruptedException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }).start();
        }
    }

    @Override
    public void sendEof() {
        start = false;
    }

    @Override
    public void asrClose() {

    }
}

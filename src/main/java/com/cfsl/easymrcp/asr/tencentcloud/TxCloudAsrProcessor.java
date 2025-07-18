package com.cfsl.easymrcp.asr.tencentcloud;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class TxCloudAsrProcessor extends AsrHandler {

    TxCloudAsrConfig txCloudConfig;
    TxCloudAsrClient txCloudClient;
    AsrCallback txCloudCallback;

    public TxCloudAsrProcessor(TxCloudAsrConfig txCloudConfig) {
        this.txCloudConfig = txCloudConfig;
    }

    @Override
    public void create() {
        txCloudCallback = new AsrCallback() {
            @Override
            public void apply(String action, String msg) {
                getCallback().apply(action, msg);
            }
        };
        txCloudClient = new TxCloudAsrClient(txCloudConfig, txCloudCallback);
        txCloudClient.create();
        // 必须执行，此时asr创建成功，并且开始识别
        // 腾讯云sdk已经进行了封装，解决了异步问题，同样需要执行countDown
        countDownLatch.countDown();
    }

    @Override
    public void receive(byte[] pcmData) {
        txCloudClient.receive(pcmData);
    }

    @Override
    public void sendEof() {
        txCloudClient.sendEof();
    }

    @Override
    public void asrClose() {
        txCloudClient.asrClose();
    }
}

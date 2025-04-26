package com.cfsl.easymrcp.asr.funasr;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class FunAsrProcessor extends AsrHandler {
    String srvIp;
    String srvPort;
    FunasrWsClient funasrWsClient;
    FunasrConfig funasrConfig;
    AsrCallback funasrCallback;

    public FunAsrProcessor(FunasrConfig funasrConfig) {
        this.funasrConfig = funasrConfig;
        this.srvIp = funasrConfig.getSrvIp();
        this.srvPort = funasrConfig.getSrvPort();
    }


    @Override
    public void create() {
        try {
            String wsAddress = "ws://" + srvIp + ":" + srvPort;
            funasrCallback = new AsrCallback() {
                @Override
                public void apply(String msg) {
                    getCallback().apply(msg);
                }
            };
            funasrWsClient = new FunasrWsClient(new URI(wsAddress), funasrCallback, stop, getCountDownLatch());
            funasrWsClient.setMode(funasrConfig.getMode());
            funasrWsClient.setHotwords(funasrConfig.getHotwords());
            funasrWsClient.setFsthotwords(funasrConfig.getFsthotwords());
            funasrWsClient.setStrChunkSize(funasrConfig.getStrChunkSize());
            funasrWsClient.setChunkInterval(funasrConfig.getChunkInterval());
            funasrWsClient.connect();
            System.out.println("wsAddress:" + wsAddress);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("e:" + e);
        }
    }

    @Override
    public void receive(byte[] pcmData) {
        funasrWsClient.recPcm(pcmData);
    }

    @Override
    public void sendEof() {

    }

    @Override
    public void asrClose() {
        funasrWsClient.sendEof();
        stop = true;
        log.info("FunAsrProcessor close");
    }
}

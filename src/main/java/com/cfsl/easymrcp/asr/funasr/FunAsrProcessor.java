package com.cfsl.easymrcp.asr.funasr;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.utils.SipUtils;
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
                public void apply(String action, String msg) {
                    getCallback().apply(action, msg);
                }
            };
            funasrWsClient = new FunasrWsClient(new URI(wsAddress), funasrCallback, stop, getCountDownLatch(), getCallId(), getPushAsrRealtimeResult());
            funasrWsClient.setMode(funasrConfig.getMode());
            funasrWsClient.setHotwords(funasrConfig.getHotwords());
            funasrWsClient.setFsthotwords(funasrConfig.getFsthotwords());
            funasrWsClient.setStrChunkSize(funasrConfig.getStrChunkSize());
            funasrWsClient.setChunkInterval(funasrConfig.getChunkInterval());
            funasrWsClient.connect();
            log.info("wsAddress:{}", wsAddress);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
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

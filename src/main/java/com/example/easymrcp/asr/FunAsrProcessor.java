package com.example.easymrcp.asr;

import com.example.easymrcp.mrcp.AsrCallback;
import com.example.easymrcp.rtp.FunasrWsClient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class FunAsrProcessor extends AsrHandler {
    static String strChunkSize = "5,10,5";
    static int chunkInterval = 10;
    static int sendChunkSize = 1920;
    static String srvIp = "172.16.2.204";
    static String srvPort = "10096";
    FunasrWsClient funasrWsClient;
    AsrCallback funasrCallback;

    @Override
    public void create() {
        try {
            int RATE = 8000;
            String[] chunkList = strChunkSize.split(",");
            int int_chunk_size = 60 * Integer.valueOf(chunkList[1].trim()) / chunkInterval;
            int CHUNK = Integer.valueOf(RATE / 1000 * int_chunk_size);
            int stride = Integer.valueOf(60 * Integer.valueOf(chunkList[1].trim()) / chunkInterval / 1000 * 8000 * 2);
            System.out.println("chunk_size:" + int_chunk_size);
            System.out.println("CHUNK:" + CHUNK);
            System.out.println("stride:" + stride);
            sendChunkSize = CHUNK * 2;

            String wsAddress = "ws://" + srvIp + ":" + srvPort;

            funasrCallback = new AsrCallback() {
                @Override
                public void apply(String msg) {
                    getCallback().apply(msg);
                }
            };
            funasrWsClient = new FunasrWsClient(new URI(wsAddress), funasrCallback, stop);
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
    public void asrClose() {
        funasrWsClient.sendEof();
        stop = true;
        log.info("FunAsrProcessor close");
    }
}

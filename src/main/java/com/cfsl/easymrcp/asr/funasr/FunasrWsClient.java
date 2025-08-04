//
// Copyright FunASR (https://github.com/alibaba-damo-academy/FunASR). All Rights
// Reserved. MIT License  (https://opensource.org/licenses/MIT)
//
/*
 * // 2022-2023 by zhaomingwork@qq.com
 */
// java FunasrWsClient
// usage:  FunasrWsClient [-h] [--port PORT] [--host HOST] [--audio_in AUDIO_IN] [--num_threads NUM_THREADS]
//                 [--chunk_size CHUNK_SIZE] [--chunk_interval CHUNK_INTERVAL] [--mode MODE]
package com.cfsl.easymrcp.asr.funasr;

import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.utils.SipUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
/** This example demonstrates how to connect to websocket server. */
public class FunasrWsClient extends WebSocketClient {
    private boolean isParagraphOver;
    private boolean iseof = true;
    @Setter
    String mode = "2pass";
    @Setter
    String hotwords = "";
    @Setter
    String fsthotwords = "";
    @Setter
    String strChunkSize = "5,10,5";
    @Setter
    int chunkInterval = 10;
    AsrCallback callback;
    Boolean stop;
    CountDownLatch countDownLatch;

    public FunasrWsClient(URI serverURI, AsrCallback callback, Boolean stop, CountDownLatch countDownLatch) {
        super(serverURI);
        this.callback = callback;
        this.stop = stop;
        this.countDownLatch = countDownLatch;
    }

    // send json at first time
    public void sendJson(
            String mode, String strChunkSize, int chunkInterval, String wavName, boolean isSpeaking) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("mode", mode);
            JSONArray array = new JSONArray();
            String[] chunkList = strChunkSize.split(",");
            for (int i = 0; i < chunkList.length; i++) {
                array.add(Integer.valueOf(chunkList[i].trim()));
            }

            obj.put("chunk_size", array);
            obj.put("chunk_interval", new Integer(chunkInterval));
            obj.put("wav_name", wavName);

            if (hotwords.trim().length() > 0) {
                String regex = "\\d+";
                JSONObject jsonitems = new JSONObject();
                String[] items = hotwords.trim().split(" ");
                Pattern pattern = Pattern.compile(regex);
                String tmpWords = "";
                for (int i = 0; i < items.length; i++) {
                    Matcher matcher = pattern.matcher(items[i]);
                    if (matcher.matches()) {
                        jsonitems.put(tmpWords.trim(), items[i].trim());
                        tmpWords = "";
                        continue;
                    }
                    tmpWords = tmpWords + items[i] + " ";
                }
                obj.put("hotwords", jsonitems.toString());
            }

            obj.put("wav_format", "pcm");
            if (isSpeaking) {
                obj.put("is_speaking", new Boolean(true));
            } else {
                obj.put("is_speaking", new Boolean(false));
            }
            log.info("sendJson: " + obj);
            // return;

            send(obj.toString());

            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // send json at end of wav
    public void sendEof() {
        try {
            JSONObject obj = new JSONObject();

            obj.put("is_speaking", new Boolean(false));

            log.info("sendEof: " + obj);
            // return;

            send(obj.toString());
            iseof = true;
            close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        sendJson(mode, strChunkSize, chunkInterval, "test1111", true);
    }


    public void recPcm(byte[] pcmData) {
        send(pcmData);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        start();
        // 必须执行，此时asr创建成功，并且开始识别
        countDownLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        JSONObject jsonObject = new JSONObject();
        JSONParser jsonParser = new JSONParser();
        log.info("received: " + message);
        try {
            jsonObject = (JSONObject) jsonParser.parse(message);
            String result = jsonObject.get("text").toString();
            log.info("text: " + result);
            if (isParagraphOver) SipUtils.executeTask(() -> callback.apply(ASRConstant.Interrupt, "打断"));
            isParagraphOver = false;
            if (!stop && !result.isEmpty() && (jsonObject.containsKey("timestamp") || (jsonObject.containsKey("mode") && jsonObject.get("mode").equals("2pass-offline")))) {
                SipUtils.executeTask(() -> callback.apply(ASRConstant.Result, result));
                isParagraphOver = true;
            }
            if (jsonObject.containsKey("timestamp")) {
                log.info("timestamp: " + jsonObject.get("timestamp"));
            }
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
        }
        if (iseof && mode.equals("offline") && !jsonObject.containsKey("is_final")) {
            close();
        }

        if (iseof && mode.equals("offline") && jsonObject.containsKey("is_final") && jsonObject.get("is_final").equals("false")) {
            close();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

        log.info(
                "Connection closed by "
                        + (remote ? "remote peer" : "us")
                        + " Code: "
                        + code
                        + " Reason: "
                        + reason);
    }

    @Override
    public void onError(Exception ex) {
        log.info("ex: " + ex);
        ex.printStackTrace();
        // if the error is fatal then onClose will be called additionally
    }
}

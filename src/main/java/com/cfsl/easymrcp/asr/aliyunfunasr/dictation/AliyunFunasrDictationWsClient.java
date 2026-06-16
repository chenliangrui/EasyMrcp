package com.cfsl.easymrcp.asr.aliyunfunasr.dictation;

import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.asr.aliyunfunasr.AliyunFunasrConfig;
import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.utils.SipUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
/**
 * 阿里云 FunASR 在线一句话识别 WebSocket 协议适配器。
 */
public class AliyunFunasrDictationWsClient extends WebSocketListener {

    private final AliyunFunasrConfig config;
    private final String taskId;
    private final String callId;
    private final AsrCallback callback;
    private final boolean stop;
    private final CountDownLatch countDownLatch;
    private final AtomicBoolean interruptEnable;
    private final AtomicBoolean pushAsrRealtimeResult;
    private final AtomicBoolean terminalStateReached = new AtomicBoolean(false);

    private WebSocket webSocket;
    private boolean paragraphOpen = true;
    private boolean finishTaskSent;

    public AliyunFunasrDictationWsClient(
            AliyunFunasrConfig config,
            String taskId,
            String callId,
            AsrCallback callback,
            Boolean stop,
            CountDownLatch countDownLatch,
            AtomicBoolean interruptEnable,
            AtomicBoolean pushAsrRealtimeResult) {
        this.config = config;
        this.taskId = taskId;
        this.callId = callId;
        this.callback = callback;
        this.stop = Boolean.TRUE.equals(stop);
        this.countDownLatch = countDownLatch;
        this.interruptEnable = interruptEnable;
        this.pushAsrRealtimeResult = pushAsrRealtimeResult;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        this.webSocket = webSocket;
        boolean accepted = webSocket.send(buildRunTaskFrame().toString());
        if (!accepted) {
            terminalStateReached.set(true);
            releaseStartLatch();
            log.warn("阿里云 FunASR 在线一句话识别 run-task 发送被拒绝，taskId={}", taskId);
            webSocket.close(1000, "run-task 发送被拒绝");
            webSocket.cancel();
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        JsonObject message;
        try {
            message = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException ex) {
            log.warn("解析阿里云 FunASR WebSocket 消息失败：{}", text, ex);
            return;
        }

        JsonObject header = getObject(message, "header");
        if (header == null) {
            return;
        }

        String event = getString(header, "event");
        if (event == null) {
            return;
        }

        switch (event) {
            case "task-started":
                releaseStartLatch();
                break;
            case "result-generated":
                if (!terminalStateReached.get()) {
                    handleResultGenerated(message);
                }
                break;
            case "task-finished":
                terminalStateReached.set(true);
                log.debug("阿里云 FunASR 在线一句话识别任务结束，taskId={}", taskId);
                break;
            case "task-failed":
                releaseStartLatch();
                terminalStateReached.set(true);
                log.warn("阿里云 FunASR 在线一句话识别任务失败，taskId={}", taskId);
                break;
            default:
                log.debug("忽略阿里云 FunASR 在线一句话识别事件 {}，taskId={}", event, taskId);
                break;
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        releaseStartLatch();
        terminalStateReached.set(true);
        log.error("阿里云 FunASR 在线一句话识别 WebSocket 异常，taskId={}", taskId, t);
        if (response != null) {
            response.close();
        }
    }

    public void sendAudio(byte[] audio) {
        if (webSocket == null || audio == null) {
            return;
        }
        webSocket.send(ByteString.of(audio));
    }

    public void sendFinishTask() {
        if (webSocket == null || finishTaskSent) {
            return;
        }
        boolean accepted = webSocket.send(buildFinishTaskFrame().toString());
        if (accepted) {
            finishTaskSent = true;
        }
    }

    public void closeSocket(String reason) {
        if (webSocket != null) {
            webSocket.close(1000, reason == null ? "" : reason);
        }
    }

    public boolean isFinishTaskSent() {
        return finishTaskSent;
    }

    private void handleResultGenerated(JsonObject message) {
        JsonObject sentence = getSentence(message);
        if (sentence == null || getBoolean(sentence, "heartbeat")) {
            return;
        }

        String resultText = getString(sentence, "text");
        if (resultText == null || resultText.trim().isEmpty()) {
            return;
        }

        boolean sentenceEnd = getBoolean(sentence, "sentence_end");
        if (!sentenceEnd && paragraphOpen && isEnabled(interruptEnable)) {
            callback.apply(ASRConstant.Interrupt, resultText);
            paragraphOpen = false;
        }
        if (!sentenceEnd && isEnabled(pushAsrRealtimeResult)) {
            SipUtils.sendAsrRealTimeResultEvent(callId, EMConstant.ALIYUN_FUNASR, resultText);
        }
        if (sentenceEnd) {
            if (!stop) {
                callback.apply(ASRConstant.Result, resultText);
            }
            paragraphOpen = true;
        }
    }

    private JsonObject buildRunTaskFrame() {
        JsonObject frame = new JsonObject();
        JsonObject header = new JsonObject();
        header.addProperty("action", "run-task");
        header.addProperty("task_id", taskId);
        header.addProperty("streaming", "duplex");
        frame.add("header", header);

        JsonObject payload = new JsonObject();
        payload.addProperty("task_group", "audio");
        payload.addProperty("task", "asr");
        payload.addProperty("function", "recognition");
        if (config != null && config.getModel() != null) {
            payload.addProperty("model", config.getModel());
        }

        JsonObject parameters = new JsonObject();
        if (config != null) {
            addString(parameters, "format", config.getFormat());
            addInteger(parameters, "sample_rate", config.getSampleRate());
            addInteger(parameters, "max_sentence_silence", config.getMaxSentenceSilence());
            addString(parameters, "vocabulary_id", config.getVocabularyId());
            addLanguageHints(parameters, config.getLanguageHints());
            addBoolean(parameters, "semantic_punctuation_enabled", config.getSemanticPunctuationEnabled());
            addBoolean(parameters, "heartbeat", config.getHeartbeat());
        }
        payload.add("parameters", parameters);
        payload.add("input", new JsonObject());
        frame.add("payload", payload);
        return frame;
    }

    private JsonObject buildFinishTaskFrame() {
        JsonObject frame = new JsonObject();
        JsonObject header = new JsonObject();
        header.addProperty("action", "finish-task");
        header.addProperty("task_id", taskId);
        header.addProperty("streaming", "duplex");
        frame.add("header", header);

        JsonObject payload = new JsonObject();
        payload.add("input", new JsonObject());
        frame.add("payload", payload);
        return frame;
    }

    private JsonObject getSentence(JsonObject message) {
        JsonObject payload = getObject(message, "payload");
        JsonObject output = getObject(payload, "output");
        return getObject(output, "sentence");
    }

    private void addLanguageHints(JsonObject parameters, String languageHintsValue) {
        if (languageHintsValue == null || languageHintsValue.trim().isEmpty()) {
            return;
        }
        JsonArray languageHints = new JsonArray();
        String[] values = languageHintsValue.split(",");
        for (String value : values) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                languageHints.add(trimmed);
            }
        }
        if (languageHints.size() > 0) {
            parameters.add("language_hints", languageHints);
        }
    }

    private void addString(JsonObject target, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.addProperty(key, value);
        }
    }

    private void addInteger(JsonObject target, String key, Integer value) {
        if (value != null) {
            target.addProperty(key, value);
        }
    }

    private void addBoolean(JsonObject target, String key, Boolean value) {
        if (value != null) {
            target.addProperty(key, value);
        }
    }

    private boolean isEnabled(AtomicBoolean flag) {
        return flag != null && flag.get();
    }

    private void releaseStartLatch() {
        if (countDownLatch != null) {
            countDownLatch.countDown();
        }
    }

    private JsonObject getObject(JsonObject root, String memberName) {
        if (root == null || !root.has(memberName)) {
            return null;
        }
        JsonElement element = root.get(memberName);
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private String getString(JsonObject root, String memberName) {
        if (root == null || !root.has(memberName)) {
            return null;
        }
        JsonElement element = root.get(memberName);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private boolean getBoolean(JsonObject root, String memberName) {
        if (root == null || !root.has(memberName)) {
            return false;
        }
        JsonElement element = root.get(memberName);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }
}

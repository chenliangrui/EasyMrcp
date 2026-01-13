package com.cfsl.easymrcp.asr.xfyun.dictation;

import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.utils.SipUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 讯飞云实时语音听写（一句话语音识别）
 * https://www.xfyun.cn/services/voicedictation
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class XfyunDictationWsClient extends WebSocketListener {
    private String callId;
    private boolean isParagraphOver = true;
    private String hostUrl; //中英文，http url 不支持解析 ws/wss schema
    // private static final String hostUrl = "https://iat-niche-api.xfyun.cn/v2/iat";//小语种
    private String appid; //在控制台-我的应用获取
    private String apiSecret; //在控制台-我的应用-语音听写（流式版）获取
    private String apiKey; //在控制台-我的应用-语音听写（流式版）获取

    public int StatusFirstFrame = 0;
    public int StatusContinueFrame = 1;
    public int StatusLastFrame = 2;
    public Gson json = new Gson();
    XfyunDictationWsClient.Decoder decoder = new XfyunDictationWsClient.Decoder();
    // 开始时间
    private static Date dateBegin = new Date();
    // 结束时间
    private static Date dateEnd = new Date();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd HH:mm:ss.SSS");
    WebSocket webSocket;
    AsrCallback callback;
    Boolean stop;
    CountDownLatch countDownLatch;
    // 是否可以打断tts
    AtomicBoolean interruptEnable;
    AtomicBoolean pushAsrRealtimeResult;

    public XfyunDictationWsClient(AsrCallback xfyunCallback, Boolean stop, CountDownLatch countDownLatch, AtomicBoolean interruptEnable, String callId, AtomicBoolean pushAsrRealtimeResult) {
        this.callback = xfyunCallback;
        this.stop = stop;
        this.countDownLatch = countDownLatch;
        this.interruptEnable = interruptEnable;
        this.callId = callId;
        this.pushAsrRealtimeResult = pushAsrRealtimeResult;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        super.onOpen(webSocket, response);
        this.webSocket = webSocket;
        start();
        dateBegin = new Date();
        // 必须执行，此时asr创建成功，并且开始识别
        countDownLatch.countDown();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        super.onMessage(webSocket, text);
        //log.info(text);
        XfyunDictationWsClient.ResponseData resp = json.fromJson(text, XfyunDictationWsClient.ResponseData.class);
        if (resp != null) {
            if (resp.getCode() != 0) {
                log.info("code=>{} error=>{} sid={}", resp.getCode(), resp.getMessage(), resp.getSid());
                log.info("错误码查询链接：https://www.xfyun.cn/document/error-code");
                // 发生错误时，主动关闭WebSocket连接
                webSocket.close(1000, "Error occurred");
                return;
            }
            if (resp.getData() != null) {
                if (resp.getData().getResult() != null) {
                    XfyunDictationWsClient.Text te = resp.getData().getResult().getText();
                    log.info(te.toString());
                    try {
                        decoder.decode(te);
                        String midResult = decoder.toString();
                        if (isParagraphOver && interruptEnable.get() && !midResult.isEmpty()) SipUtils.executeTask(() -> callback.apply(ASRConstant.Interrupt, "打断"));
                        isParagraphOver = false;
                        log.info("中间识别结果 ==》" + midResult);
                        if (pushAsrRealtimeResult.get() && !midResult.isEmpty()) {
                            // 实时推送asr识别结果
                            SipUtils.sendAsrRealTimeResultEvent(callId, EMConstant.XFYUN, midResult);
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
                if (resp.getData().getStatus() == 2) {
                    log.info("session end ");
                    dateEnd = new Date();
                    log.info(sdf.format(dateBegin) + "开始");
                    log.info(sdf.format(dateEnd) + "结束");
                    log.info("耗时:" + (dateEnd.getTime() - dateBegin.getTime()) + "ms");
                    String result = decoder.toString();
                    log.info("最终识别结果 ==》" + result);
                    log.info("本次识别sid ==》" + resp.getSid());
                    if (!stop && !result.isEmpty()) SipUtils.executeTask(() -> callback.apply(ASRConstant.Result, result));
                    isParagraphOver = true;
                    decoder.discard();
                    webSocket.close(1000, "Normal closure after completion");
                } else {
                    // todo 根据返回的数据处理
                }
            }
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        super.onFailure(webSocket, t, response);
        log.error("WebSocket failure", t);
        try {
            if (null != response) {
                int code = response.code();
                log.info("onFailure code:" + code);
                try {
                    if (response.body() != null) {
                        log.info("onFailure body:" + response.body().string());
                    }
                } catch (IOException e) {
                    log.error("Error reading response body", e);
                } finally {
                    response.close(); // 确保关闭Response
                }
                
                if (101 != code) {
                    log.info("connection failed");
                    webSocket.close(1000, "Connection failed");
                }
            }
        } catch (Exception e) {
            log.error("Error in onFailure handler", e);
        }
    }

    public void start() {
        JsonObject frame = new JsonObject();
        JsonObject business = new JsonObject();  //第一帧必须发送
        JsonObject common = new JsonObject();  //第一帧必须发送
        JsonObject data = new JsonObject();  //每一帧都要发送
        // 填充common
        common.addProperty("app_id", appid);
        //填充business
        business.addProperty("language", "zh_cn");
        //business.addProperty("language", "en_us");//英文
        //business.addProperty("language", "ja_jp");//日语，在控制台可添加试用或购买
        //business.addProperty("language", "ko_kr");//韩语，在控制台可添加试用或购买
        //business.addProperty("language", "ru-ru");//俄语，在控制台可添加试用或购买
        business.addProperty("domain", "iat");
        business.addProperty("accent", "mandarin");//中文方言请在控制台添加试用，添加后即展示相应参数值
        //business.addProperty("nunum", 0);
        //business.addProperty("ptt", 0);//标点符号
        //business.addProperty("rlang", "zh-hk"); // zh-cn :简体中文（默认值）zh-hk :繁体香港(若未授权不生效，在控制台可免费开通)
        //business.addProperty("vinfo", 1);
        business.addProperty("dwa", "wpgs");//动态修正(若未授权不生效，在控制台可免费开通)
        //business.addProperty("nbest", 5);// 句子多候选(若未授权不生效，在控制台可免费开通)
        //business.addProperty("wbest", 3);// 词级多候选(若未授权不生效，在控制台可免费开通)
        //填充data
        data.addProperty("status", StatusFirstFrame);
        data.addProperty("format", "audio/L16;rate=8000");
        data.addProperty("encoding", "raw");
        data.addProperty("audio", Base64.getEncoder().encodeToString(Arrays.copyOf(new byte[]{}, 0)));
        //填充frame
        frame.add("common", common);
        frame.add("business", business);
        frame.add("data", data);
        webSocket.send(frame.toString());
    }

    public void sendBuffer(byte[] buffer) {
        JsonObject frame1 = new JsonObject();
        JsonObject data1 = new JsonObject();
        data1.addProperty("status", StatusContinueFrame);
        data1.addProperty("format", "audio/L16;rate=8000");
        data1.addProperty("encoding", "raw");
        data1.addProperty("audio", Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, buffer.length)));
        frame1.add("data", data1);
        webSocket.send(frame1.toString());
    }

    public void sendEof() {
        JsonObject frame2 = new JsonObject();
        JsonObject data2 = new JsonObject();
        data2.addProperty("status", StatusLastFrame);
        data2.addProperty("audio", "");
        data2.addProperty("format", "audio/L16;rate=8000");
        data2.addProperty("encoding", "raw");
        frame2.add("data", data2);
        webSocket.send(frame2.toString());
    }

    public static String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(hostUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        StringBuilder builder = new StringBuilder("host: ").append(url.getHost()).append("\n").//
                append("date: ").append(date).append("\n").//
                append("GET ").append(url.getPath()).append(" HTTP/1.1");
        //log.info(builder);
        Charset charset = Charset.forName("UTF-8");
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(charset), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = Base64.getEncoder().encodeToString(hexDigits);

        //log.info(sha);
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line", sha);
        //log.info(authorization);
        HttpUrl httpUrl = HttpUrl.parse("https://" + url.getHost() + url.getPath()).newBuilder().//
                addQueryParameter("authorization", Base64.getEncoder().encodeToString(authorization.getBytes(charset))).//
                addQueryParameter("date", date).//
                addQueryParameter("host", url.getHost()).//
                build();
        return httpUrl.toString();
    }

    public static class ResponseData {
        private int code;
        private String message;
        private String sid;
        private XfyunDictationWsClient.Data data;

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return this.message;
        }

        public String getSid() {
            return sid;
        }

        public XfyunDictationWsClient.Data getData() {
            return data;
        }
    }

    public static class Data {
        private int status;
        private XfyunDictationWsClient.Result result;

        public int getStatus() {
            return status;
        }

        public XfyunDictationWsClient.Result getResult() {
            return result;
        }
    }

    public static class Result {
        int bg;
        int ed;
        String pgs;
        int[] rg;
        int sn;
        XfyunDictationWsClient.Ws[] ws;
        boolean ls;
        JsonObject vad;

        public XfyunDictationWsClient.Text getText() {
            XfyunDictationWsClient.Text text = new XfyunDictationWsClient.Text();
            StringBuilder sb = new StringBuilder();
            for (XfyunDictationWsClient.Ws ws : this.ws) {
                sb.append(ws.cw[0].w);
            }
            text.sn = this.sn;
            text.text = sb.toString();
            text.sn = this.sn;
            text.rg = this.rg;
            text.pgs = this.pgs;
            text.bg = this.bg;
            text.ed = this.ed;
            text.ls = this.ls;
            text.vad = this.vad == null ? null : this.vad;
            return text;
        }
    }

    public static class Ws {
        XfyunDictationWsClient.Cw[] cw;
        int bg;
        int ed;
    }

    public static class Cw {
        int sc;
        String w;
    }

    public static class Text {
        int sn;
        int bg;
        int ed;
        String text;
        String pgs;
        int[] rg;
        boolean deleted;
        boolean ls;
        JsonObject vad;

        @Override
        public String toString() {
            return "Text{" +
                    "bg=" + bg +
                    ", ed=" + ed +
                    ", ls=" + ls +
                    ", sn=" + sn +
                    ", text='" + text + '\'' +
                    ", pgs=" + pgs +
                    ", rg=" + Arrays.toString(rg) +
                    ", deleted=" + deleted +
                    ", vad=" + (vad == null ? "null" : vad.getAsJsonArray("ws").toString()) +
                    '}';
        }
    }

    //解析返回数据，仅供参考
    public static class Decoder {
        private XfyunDictationWsClient.Text[] texts;
        private int defc = 10;

        public Decoder() {
            this.texts = new XfyunDictationWsClient.Text[this.defc];
        }

        public synchronized void decode(XfyunDictationWsClient.Text text) {
            if (text.sn >= this.defc) {
                this.resize();
            }
            if ("rpl".equals(text.pgs)) {
                for (int i = text.rg[0]; i <= text.rg[1]; i++) {
                    this.texts[i].deleted = true;
                }
            }
            this.texts[text.sn] = text;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (XfyunDictationWsClient.Text t : this.texts) {
                if (t != null && !t.deleted) {
                    sb.append(t.text);
                }
            }
            return sb.toString();
        }

        public void resize() {
            int oc = this.defc;
            this.defc <<= 1;
            XfyunDictationWsClient.Text[] old = this.texts;
            this.texts = new XfyunDictationWsClient.Text[this.defc];
            for (int i = 0; i < oc; i++) {
                this.texts[i] = old[i];
            }
        }

        public void discard() {
            for (int i = 0; i < this.texts.length; i++) {
                this.texts[i] = null;
            }
        }
    }
}

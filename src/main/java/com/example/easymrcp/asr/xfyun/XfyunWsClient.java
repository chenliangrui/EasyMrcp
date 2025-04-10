package com.example.easymrcp.asr.xfyun;

import com.example.easymrcp.mrcp.AsrCallback;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

public class XfyunWsClient extends WebSocketListener {
    private static final String hostUrl = "https://iat-api.xfyun.cn/v2/iat"; //中英文，http url 不支持解析 ws/wss schema
    // private static final String hostUrl = "https://iat-niche-api.xfyun.cn/v2/iat";//小语种
    private static final String appid = "c22aeabc"; //在控制台-我的应用获取
    private static final String apiSecret = "NjAwYWYyZDQ5ZjJjNjZhY2UzMWJjMThl"; //在控制台-我的应用-语音听写（流式版）获取
    private static final String apiKey = "f23da979c109e5c31c0dc9dd5d3052a5"; //在控制台-我的应用-语音听写（流式版）获取
    private static final String file = "C:\\Users\\25212\\Downloads\\iat_ws_java_demo\\iat_ws_java_demo\\resource\\iat\\16k_10.pcm"; // 中文
    public static final int StatusFirstFrame = 0;
    public static final int StatusContinueFrame = 1;
    public static final int StatusLastFrame = 2;
    public static final Gson json = new Gson();
    XfyunWsClient.Decoder decoder = new XfyunWsClient.Decoder();
    // 开始时间
    private static Date dateBegin = new Date();
    // 结束时间
    private static Date dateEnd = new Date();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd HH:mm:ss.SSS");
    WebSocket webSocket;
    AsrCallback callback;
    Boolean stop;

    public XfyunWsClient(AsrCallback funasrCallback, Boolean stop) {
        this.callback = funasrCallback;
        this.stop = stop;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        super.onOpen(webSocket, response);
        this.webSocket = webSocket;
        start();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        super.onMessage(webSocket, text);
        //System.out.println(text);
        XfyunWsClient.ResponseData resp = json.fromJson(text, XfyunWsClient.ResponseData.class);
        if (resp != null) {
            if (resp.getCode() != 0) {
                System.out.println( "code=>" + resp.getCode() + " error=>" + resp.getMessage() + " sid=" + resp.getSid());
                System.out.println( "错误码查询链接：https://www.xfyun.cn/document/error-code");
                return;
            }
            if (resp.getData() != null) {
                if (resp.getData().getResult() != null) {
                    XfyunWsClient.Text te = resp.getData().getResult().getText();
                    //System.out.println(te.toString());
                    try {
                        decoder.decode(te);
                        System.out.println("中间识别结果 ==》" + decoder.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (resp.getData().getStatus() == 2) {
                    // todo  resp.data.status ==2 说明数据全部返回完毕，可以关闭连接，释放资源
                    System.out.println("session end ");
                    dateEnd = new Date();
                    System.out.println(sdf.format(dateBegin) + "开始");
                    System.out.println(sdf.format(dateEnd) + "结束");
                    System.out.println("耗时:" + (dateEnd.getTime() - dateBegin.getTime()) + "ms");
                    String result = decoder.toString();
                    System.out.println("最终识别结果 ==》" + result);
                    System.out.println("本次识别sid ==》" + resp.getSid());
                    if (!stop && !result.isEmpty()) callback.apply(result);
                    decoder.discard();
                    webSocket.close(1000, "");
                } else {
                    // todo 根据返回的数据处理
                }
            }
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        super.onFailure(webSocket, t, response);
        try {
            if (null != response) {
                int code = response.code();
                System.out.println("onFailure code:" + code);
                System.out.println("onFailure body:" + response.body().string());
                if (101 != code) {
                    System.out.println("connection failed");
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
        //System.out.println(builder);
        Charset charset = Charset.forName("UTF-8");
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(charset), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = Base64.getEncoder().encodeToString(hexDigits);

        //System.out.println(sha);
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line", sha);
        //System.out.println(authorization);
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
        private XfyunWsClient.Data data;
        public int getCode() {
            return code;
        }
        public String getMessage() {
            return this.message;
        }
        public String getSid() {
            return sid;
        }
        public XfyunWsClient.Data getData() {
            return data;
        }
    }
    public static class Data {
        private int status;
        private XfyunWsClient.Result result;
        public int getStatus() {
            return status;
        }
        public XfyunWsClient.Result getResult() {
            return result;
        }
    }
    public static class Result {
        int bg;
        int ed;
        String pgs;
        int[] rg;
        int sn;
        XfyunWsClient.Ws[] ws;
        boolean ls;
        JsonObject vad;
        public XfyunWsClient.Text getText() {
            XfyunWsClient.Text text = new XfyunWsClient.Text();
            StringBuilder sb = new StringBuilder();
            for (XfyunWsClient.Ws ws : this.ws) {
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
            text.vad = this.vad==null ? null : this.vad;
            return text;
        }
    }
    public static class Ws {
        XfyunWsClient.Cw[] cw;
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
                    ", vad=" + (vad==null ? "null" : vad.getAsJsonArray("ws").toString()) +
                    '}';
        }
    }
    //解析返回数据，仅供参考
    public static class Decoder {
        private XfyunWsClient.Text[] texts;
        private int defc = 10;
        public Decoder() {
            this.texts = new XfyunWsClient.Text[this.defc];
        }
        public synchronized void decode(XfyunWsClient.Text text) {
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
            for (XfyunWsClient.Text t : this.texts) {
                if (t != null && !t.deleted) {
                    sb.append(t.text);
                }
            }
            return sb.toString();
        }
        public void resize() {
            int oc = this.defc;
            this.defc <<= 1;
            XfyunWsClient.Text[] old = this.texts;
            this.texts = new XfyunWsClient.Text[this.defc];
            for (int i = 0; i < oc; i++) {
                this.texts[i] = old[i];
            }
        }
        public void discard(){
            for(int i=0;i<this.texts.length;i++){
                this.texts[i]= null;
            }
        }
    }
}

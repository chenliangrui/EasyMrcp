package com.example.easymrcp.tts.xfyun;

import com.example.easymrcp.tts.TTSConstant;
import com.example.easymrcp.tts.TtsHandler;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
public class XfyunTtsProcessor extends TtsHandler {
    // 地址与鉴权信息
    public String hostUrl;
    // 均到控制台-语音合成页面获取
    public String APPID;
    public String APISecret;
    public String APIKey;
    // 合成文本
    public String text;
    // 合成文本编码格式
    public String TTE; // 小语种必须使用UNICODE编码作为值
    // 发音人参数。到控制台-我的应用-语音合成-添加试用或购买发音人，添加后即显示该发音人参数值，若试用未添加的发音人会报错11200
    public String VCN;
    // 合成文件存储地址以及名称
    public String OUTPUT_FILE_PATH = "src/main/resources/" + System.currentTimeMillis() + ".pcm";
    // json
    public Gson gson = new Gson();
    public boolean wsCloseFlag = false;
    String wsUrl;

    public XfyunTtsProcessor(XfyunTtsConfig xfyunTtsConfig) {
        this.hostUrl = xfyunTtsConfig.hostUrl;
        this.APPID = xfyunTtsConfig.APPID;
        this.APIKey = xfyunTtsConfig.APIKey;
        this.APISecret = xfyunTtsConfig.APISecret;
        this.TTE = xfyunTtsConfig.TTE;
        this.VCN = xfyunTtsConfig.VCN;
    }

    @Override
    public void create() {
        try {
            wsUrl = getAuthUrl(hostUrl, APIKey, APISecret).replace("https://", "wss://");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void speak(String text) {
        this.text = text;
        websocketWork(wsUrl);
    }

    @Override
    public void ttsClose() {
        log.info("xfyun tts close");
    }

    // Websocket方法
    public void websocketWork(String wsUrl) {
        try {
            URI uri = new URI(wsUrl);
            WebSocketClient webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    System.out.println("ws建立连接成功...");
                }

                @Override
                public void onMessage(String text) {
                    //System.out.println(text);//打印响应参数到控制台
                    JsonParse myJsonParse = gson.fromJson(text, JsonParse.class);
                    if (myJsonParse.code != 0) {
                        System.out.println("发生错误，错误码为：" + myJsonParse.code);
                        System.out.println("本次请求的sid为：" + myJsonParse.sid);
                    }
                    if (myJsonParse.data != null) {
                        try {
                            byte[] textBase64Decode = Base64.getDecoder().decode(myJsonParse.data.audio);
                            processor.putData(textBase64Decode, textBase64Decode.length);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (myJsonParse.data.status == 2) {
                            System.out.println("本次请求的sid==>" + myJsonParse.sid);
                            // 可以关闭连接，释放资源
                            wsCloseFlag = true;
                        }
                    }
                }

                @Override
                public void onClose(int i, String s, boolean b) {
                    System.out.println("ws链接已关闭，本次请求完成...");
                }

                @Override
                public void onError(Exception e) {
                    System.out.println("发生错误 " + e.getMessage());
                }
            };
            // 建立连接
            webSocketClient.connect();
            while (!webSocketClient.getReadyState().equals(ReadyState.OPEN)) {
                //System.out.println("正在连接...");
                Thread.sleep(100);
            }
            MyThread webSocketThread = new MyThread(webSocketClient);
            webSocketThread.start();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    // 线程来发送音频与参数
    class MyThread extends Thread {
        WebSocketClient webSocketClient;

        public MyThread(WebSocketClient webSocketClient) {
            this.webSocketClient = webSocketClient;
        }

        public void run() {
            String requestJson;//请求参数json串
            try {
                requestJson = "{\n" +
                        "  \"common\": {\n" +
                        "    \"app_id\": \"" + APPID + "\"\n" +
                        "  },\n" +
                        "  \"business\": {\n" +
                        "    \"aue\": \"raw\",\n" +
                        "    \"tte\": \"" + TTE + "\",\n" +
                        "    \"auf\": \"" + "audio/L16;rate=8000" + "\",\n" +
                        "    \"ent\": \"intp65\",\n" +
                        "    \"vcn\": \"" + VCN + "\",\n" +
                        "    \"pitch\": 50,\n" +
                        "    \"speed\": 50\n" +
                        "  },\n" +
                        "  \"data\": {\n" +
                        "    \"status\": 2,\n" +
                        "    \"text\": \"" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "\"\n" +
                        //"    \"text\": \"" + Base64.getEncoder().encodeToString(TEXT.getBytes("UTF-16LE")) + "\"\n" +
                        "  }\n" +
                        "}";
                webSocketClient.send(requestJson);
                // 等待服务端返回完毕后关闭
                while (!wsCloseFlag) {
                    Thread.sleep(200);
                }
                webSocketClient.close();
                // tts语音合成结束，写入结束标志
                processor.putData(TTSConstant.TTS_END_FLAG, TTSConstant.TTS_END_FLAG.length);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    // 鉴权方法
    public String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(hostUrl);
        // 时间
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        // 拼接
        String preStr = "host: " + url.getHost() + "\n" +
                "date: " + date + "\n" +
                "GET " + url.getPath() + " HTTP/1.1";
        //System.out.println(preStr);
        // SHA256加密
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(preStr.getBytes(StandardCharsets.UTF_8));
        // Base64加密
        String sha = Base64.getEncoder().encodeToString(hexDigits);
        // 拼接
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line", sha);
        // 拼接地址
        HttpUrl httpUrl = Objects.requireNonNull(HttpUrl.parse("https://" + url.getHost() + url.getPath())).newBuilder().//
                addQueryParameter("authorization", Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8))).//
                addQueryParameter("date", date).//
                addQueryParameter("host", url.getHost()).//
                build();

        return httpUrl.toString();
    }

    //返回的json结果拆解
    class JsonParse {
        int code;
        String sid;
        Data data;
    }

    class Data {
        int status;
        String audio;
    }
}

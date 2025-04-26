package com.example.easymrcp.asr.xfyun.transliterate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.easymrcp.asr.AsrText;
import com.example.easymrcp.mrcp.AsrCallback;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * 讯飞云实时语音转写（长时间语言转写）
 * 地址:https://www.xfyun.cn/services/rtasr
 */
@Slf4j
public class XfyunTransliterateWsClient {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd HH:mm:ss.SSS");
    Boolean stop;
    MyWebSocketClient client;
    static AsrText asrText;

    public XfyunTransliterateWsClient(AsrCallback xfyunAsrCallback, Boolean stop, MyWebSocketClient client) {
        this.stop = stop;
        this.client = client;
        asrText = new AsrText(xfyunAsrCallback);
    }

    public void sendBuffer(byte[] buffer) {
        send(client, buffer);
    }

    public void sendEof() {
        send(client, "{\"end\": true}".getBytes());
        log.info(getCurrentTimeStr() + "\t发送结束标识完成");
    }

    // 生成握手参数
    public static String getHandShakeParams(String appId, String secretKey) {
        String ts = System.currentTimeMillis()/1000 + "";
        String signa = "";
        try {
            signa = EncryptUtil.HmacSHA1Encrypt(EncryptUtil.MD5(appId + ts), secretKey);
            return "?appid=" + appId + "&ts=" + ts + "&signa=" + URLEncoder.encode(signa, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public static void send(WebSocketClient client, byte[] bytes) {
        if (client.isClosed()) {
            throw new RuntimeException("client connect closed!");
        }

        client.send(bytes);
    }

    public static String getCurrentTimeStr() {
        return sdf.format(new Date());
    }

    static class MyWebSocketClient extends WebSocketClient {

        private CountDownLatch handshakeSuccess;
        private CountDownLatch connectClose;

        public MyWebSocketClient(URI serverUri, Draft protocolDraft, CountDownLatch handshakeSuccess) {
            super(serverUri, protocolDraft);
            this.handshakeSuccess = handshakeSuccess;
            if(serverUri.toString().contains("wss")){
                trustAllHosts(this);
            }
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            log.info(getCurrentTimeStr() + "\t连接建立成功！");
        }

        @Override
        public void onMessage(String msg) {
            JSONObject msgObj = JSON.parseObject(msg);
            String action = msgObj.getString("action");
            if (Objects.equals("started", action)) {
                // 握手成功
                log.info(getCurrentTimeStr() + "\t握手成功！sid: " + msgObj.getString("sid"));
                handshakeSuccess.countDown();
            } else if (Objects.equals("result", action)) {
                // 转写结果
                String data = getContent(msgObj.getString("data"));
                log.info(getCurrentTimeStr() + "\tresult: " + data);
                asrText.updateAccumulatedText(data);
                asrText.resetTimer(); // 重置倒计时器
            } else if (Objects.equals("error", action)) {
                // 连接发生错误
                log.info("Error: " + msg);
                System.exit(0);
            }
        }

        @Override
        public void onError(Exception e) {
            log.info(getCurrentTimeStr() + "\t连接发生错误：" + e.getMessage() + ", " + new Date());
            e.printStackTrace();
            System.exit(0);
        }

        @Override
        public void onClose(int arg0, String arg1, boolean arg2) {
            log.info(getCurrentTimeStr() + "\t链接关闭");
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                log.info(getCurrentTimeStr() + "\t服务端返回：" + new String(bytes.array(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        public void trustAllHosts(XfyunTransliterateWsClient.MyWebSocketClient appClient) {
            log.info("wss");
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }

                @Override
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }

                @Override
                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }
            }};

            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                appClient.setSocket(sc.getSocketFactory().createSocket());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 把转写结果解析为句子
    public static String getContent(String message) {
        log.info("message: " + message);
        StringBuffer resultBuilder = new StringBuffer();
        try {
            JSONObject messageObj = JSON.parseObject(message);
            JSONObject cn = messageObj.getJSONObject("cn");
            JSONObject st = cn.getJSONObject("st");
            JSONArray rtArr = st.getJSONArray("rt");
            for (int i = 0; i < rtArr.size(); i++) {
                JSONObject rtArrObj = rtArr.getJSONObject(i);
                JSONArray wsArr = rtArrObj.getJSONArray("ws");
                for (int j = 0; j < wsArr.size(); j++) {
                    JSONObject wsArrObj = wsArr.getJSONObject(j);
                    JSONArray cwArr = wsArrObj.getJSONArray("cw");
                    for (int k = 0; k < cwArr.size(); k++) {
                        JSONObject cwArrObj = cwArr.getJSONObject(k);
                        String wStr = cwArrObj.getString("w");
                        resultBuilder.append(wStr);
                    }
                }
            }
        } catch (Exception e) {
            return message;
        }

        return resultBuilder.toString();
    }
}

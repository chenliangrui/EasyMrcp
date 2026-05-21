package com.cfsl.easymrcp.examples.client;

import com.alibaba.fastjson.JSONObject;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * 纯 TCP client 的可运行示例入口。
 *
 * 这个 demo 演示的是：
 * 1. 连接 EasyMrcp
 * 2. 收到 ClientConnect 后启动 DetectSpeech
 * 3. 收到 RecognitionComplete 后把识别结果再用 Speak 播放出来
 *
 * 它不依赖 FreeSWITCH / ESL，适合先单独理解 EasyMrcp 的事件交互方式。
 */
public class EnhancedNettyTcpClientExample {

    public static void main(String[] args) throws InterruptedException {
        // 允许直接通过命令行传入 host / port / clientId，便于快速联调。
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9090;
        String clientId = args.length > 2 ? args[2] : UUID.randomUUID().toString();

        // 这里显式创建 EventLoopGroup，退出时再统一释放，方便看清客户端生命周期。
        EventLoopGroup group = new NioEventLoopGroup();
        CountDownLatch shutdownLatch = new CountDownLatch(1);

        EnhancedNettyTcpClient client = new EnhancedNettyTcpClient(host, port, clientId, group);

        // 服务端确认 ClientConnect 后，开始一轮 ASR 识别。
        client.registerEventCallback(TcpEventType.ClientConnect, (eventId, data) -> {
            JSONObject detectSpeechParams = new JSONObject();
            detectSpeechParams.put(ASRConstant.StartInputTimers, true);
            detectSpeechParams.put(ASRConstant.NoInputTimeout, 60000);
            detectSpeechParams.put(ASRConstant.SpeechCompleteTimeout, 800);
            detectSpeechParams.put(ASRConstant.AutomaticInterruption, true);
            client.sendEvent(TcpEventType.DetectSpeech, detectSpeechParams.toJSONString());
        });

        // 用户说话结束后，简单把识别结果再播一遍，方便看清完整回路。
        client.registerEventCallback(TcpEventType.RecognitionComplete, (eventId, data) -> {
            System.out.println("识别完成: " + data);
            client.sendEvent(UUID.randomUUID().toString(), TcpEventType.Speak, data);
        });

        // 超时没说话时，播放一条提示语。
        client.registerEventCallback(TcpEventType.NoInputTimeout, (eventId, data) -> {
            System.out.println("识别超时");
            client.sendEvent(UUID.randomUUID().toString(), TcpEventType.Speak, "您好，您还在线吗？");
        });

        // 下面几个回调主要用于观察 demo 运行状态。
        client.registerEventCallback(TcpEventType.SpeakComplete, (eventId, data) ->
                System.out.println("TTS播放完成: " + eventId));

        client.registerEventCallback(TcpEventType.SpeakInterrupted, (eventId, data) ->
                System.out.println("TTS被打断: " + eventId));

        client.registerEventCallback(TcpEventType.AsrRealTimeResult, (eventId, data) ->
                System.out.println("实时识别结果: " + data));

        // Ctrl+C 退出时，主动断开 EasyMrcp 连接并释放线程资源。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.disconnect();
            shutdownLatch.countDown();
        }));

        // 这里的 Type=call 表示按普通通话场景接入。
        JSONObject connectParams = new JSONObject();
        connectParams.put("Type", "call");
        client.connect(connectParams);

        System.out.println("EnhancedNettyTcpClient 示例已启动，按 Ctrl+C 退出");
        shutdownLatch.await();
    }
}

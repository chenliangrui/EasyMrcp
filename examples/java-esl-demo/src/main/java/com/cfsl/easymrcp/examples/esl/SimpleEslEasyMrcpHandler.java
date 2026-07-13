package com.cfsl.easymrcp.examples.esl;

import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.examples.esl.client.ASRConstant;
import com.cfsl.easymrcp.examples.esl.client.EnhancedNettyTcpClient;
import com.cfsl.easymrcp.examples.esl.client.TcpEventType;
import com.cfsl.easymrcp.examples.esl.config.EasyMrcpDemoProperties;
import io.netty.channel.EventLoopGroup;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * 单通话维度的 EasyMrcp 会话处理器。
 *
 * 这个类负责把一条 ESL 事件链路转换成一条 EasyMrcp TCP 会话链路：
 * - park 后创建 handler
 * - connect 成功后发送欢迎语和 DetectSpeech
 * - 识别完成后把结果再回发成 Speak
 * - hangup 时关闭连接
 */
@Slf4j
public class SimpleEslEasyMrcpHandler {

    private static final String WELCOME_TEXT = "您好，请开始讲话。";
    private static final String TIMEOUT_TEXT = "您好，您还在线吗？";

    /** 当前通话 UUID，ESL 和 EasyMrcp 都用它来关联同一通电话。 */
    private final String uuid;
    /** 当前通话对应的 EasyMrcp TCP 客户端。 */
    private final EnhancedNettyTcpClient mrcpClient;
    /** demo 级别配置，例如 EasyMrcp 地址。 */
    private final EasyMrcpDemoProperties properties;

    public SimpleEslEasyMrcpHandler(String uuid,
                                    EasyMrcpDemoProperties properties,
                                    EventLoopGroup eventLoopGroup) {
        this.uuid = uuid;
        this.properties = properties;
        this.mrcpClient = new EnhancedNettyTcpClient(
                properties.getHost(),
                properties.getPort(),
                uuid,
                eventLoopGroup
        );
    }

    /**
     * 启动这通电话的 EasyMrcp 会话。
     *
     * 这里会注册几个最核心的回调：
     * - ClientConnect：连上后发欢迎语并开始 DetectSpeech
     * - RecognitionComplete：把识别结果回发成 Speak
     * - NoInputTimeout：长时间没说话时播放超时提示语
     */
    public void start() {
        mrcpClient.registerEventCallback(TcpEventType.ClientConnect, (eventId, data) -> {
            log.info("EasyMrcp服务端连接成功, uuid={}", uuid);
            mrcpClient.sendEvent(UUID.randomUUID().toString(), TcpEventType.Speak, WELCOME_TEXT);

            JSONObject detectSpeechParams = new JSONObject();
            detectSpeechParams.put(ASRConstant.StartInputTimers, true);
            detectSpeechParams.put(ASRConstant.NoInputTimeout, 60000);
            detectSpeechParams.put(ASRConstant.SpeechCompleteTimeout, 800);
            detectSpeechParams.put(ASRConstant.AutomaticInterruption, true);
            mrcpClient.sendEvent(TcpEventType.DetectSpeech, detectSpeechParams.toJSONString());
        });

        mrcpClient.registerEventCallback(TcpEventType.RecognitionComplete, (eventId, data) -> {
            log.info("识别完成, uuid={}, data={}", uuid, data);
            mrcpClient.sendEvent(UUID.randomUUID().toString(), TcpEventType.Speak, data);
        });

        mrcpClient.registerEventCallback(TcpEventType.NoInputTimeout, (eventId, data) -> {
            log.info("识别超时, uuid={}", uuid);
            mrcpClient.sendEvent(UUID.randomUUID().toString(), TcpEventType.Speak, TIMEOUT_TEXT);
        });

        mrcpClient.registerEventCallback(TcpEventType.SpeakComplete, (eventId, data) ->
                log.info("TTS播放完成, uuid={}, eventId={}", uuid, eventId));

        // 这里的 Type=call 表示普通通话场景，而不是 spy 等其他模式。
        JSONObject connectParams = new JSONObject();
        connectParams.put("Type", "call");
        mrcpClient.connect(connectParams);
    }

    /** 结束这通电话的 EasyMrcp 会话。 */
    public void close() {
        mrcpClient.disconnect();
    }

    public String getUuid() {
        return uuid;
    }
}

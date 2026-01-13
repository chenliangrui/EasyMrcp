package com.cfsl.easymrcp.utils;

import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.domain.AsrRealTimeProtocol;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpEventType;
import io.netty.util.HashedWheelTimer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

@Component
public class SipUtils {
    private static final Random random = new Random((new Date()).getTime());
    @Autowired
    SipContext sipContext;
    private static MrcpManage mrcpManage;
    private static TcpClientNotifier tcpClientNotifier;

    // Netty时间轮 单例模式
    public static final HashedWheelTimer wheelTimer = new HashedWheelTimer(50, TimeUnit.MILLISECONDS);

    @Autowired
    public void setMrcpManage(MrcpManage mrcpManage) {
        SipUtils.mrcpManage = mrcpManage;
    }

    @Autowired
    public void setTcpClientNotifier(TcpClientNotifier tcpClientNotifier) {
        SipUtils.tcpClientNotifier = tcpClientNotifier;
    }

    public static void executeTask(Runnable runnable) {
        mrcpManage.executeTask(runnable);
    }

    /**
     * 实时推送asr识别结果
     * @param callId 通话callId
     * @param asrEngine asr引擎名称
     * @param data asr识别结果
     */
    public static void sendAsrRealTimeResultEvent(String callId, String asrEngine, String data) {
        AsrRealTimeProtocol asrRealTimeProtocol = new AsrRealTimeProtocol();
        asrRealTimeProtocol.setAsrEngine(asrEngine);
        asrRealTimeProtocol.setAsrResult(data);
        tcpClientNotifier.sendEvent(callId, null, TcpEventType.AsrRealTimeResult, JSONObject.toJSONString(asrRealTimeProtocol));
    }

    public static String getGUID() {
        // counter++;
        // return guidPrefix+counter;
        int r = random.nextInt();
        r = (r < 0) ? 0 - r : r; // generate a positive number
        return Integer.toString(r);
    }

    public Vector<String> getSupportProtocols(Vector<String> formatsInRequest) {
        Vector<String> useProtocol = new Vector<>();
        for (String supportProtocol : sipContext.getSupportProtocols()) {
            if (formatsInRequest.contains(supportProtocol)) {
                useProtocol.add(supportProtocol);
                break;
            }
        }
        return useProtocol;
    }
}

package com.cfsl.easymrcp.tcp.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.rtp.RtpManager;
import com.cfsl.easymrcp.sip.SipOptions;
import com.cfsl.easymrcp.sip.handle.HandleSipInit;
import com.cfsl.easymrcp.tcp.*;
import com.cfsl.easymrcp.utils.SpringUtils;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientConnectEventHandler implements MrcpEventHandler {
    MrcpManage mrcpManage;

    public ClientConnectEventHandler(MrcpManage mrcpManage) {
        this.mrcpManage = mrcpManage;
    }

    @Override
    public TcpResponse handleEvent(MrcpEvent event, TcpClientNotifier tcpClientNotifier) {
        String id = event.getId();
        JSONObject connectParams = JSON.parseObject(event.getData());
        boolean spyMode = connectParams != null && "spy".equals(connectParams.getString("Type"));

        if (spyMode) {
            // spy监听模式与普通AI通话连接互斥：ASR引擎必须由调用方在连接参数中显式指定，
            // 服务端不再为监听场景提供默认引擎。
            String asrEngineName = connectParams.getString("AsrEngine");
            if (asrEngineName == null || asrEngineName.trim().isEmpty()) {
                log.error("spy模式连接缺少AsrEngine参数, id: {}", id);
                return TcpResponse.error(id, "spy模式连接必须指定AsrEngine参数");
            }
            mrcpManage.updateConnection(id, null, asrEngineName);
            mrcpManage.setPushAsrRealtimeResult(id, false);
            // 启动spy模式，对某一路通话进行asr识别
            SipContext sipContext = SpringUtils.getBean(SipContext.class);
            RtpManager rtpManager = SpringUtils.getBean(RtpManager.class);
            SipOptions sipOptions = SpringUtils.getBean(SipOptions.class);
            HandleSipInit handleSipInit = SpringUtils.getBean(HandleSipInit.class);
            AsrHandler asrHandler = handleSipInit.initAsr(sipOptions.getFsServerIp(), 0, 8, EMConstant.VOIP_SAMPLES_PER_FRAME, 20, id);
            int rtpPort = sipContext.getAsrRtpPort();
            Channel rtpChannel = rtpManager.createRtpChannel(id, rtpPort, asrHandler.getNettyAsrRtpProcessor());
            JSONObject connectParamsRes = new JSONObject();
            connectParamsRes.put("rtpPort", rtpPort);
            tcpClientNotifier.sendEvent(id, null, TcpEventType.ClientConnect, connectParamsRes.toJSONString());
            return TcpResponse.success(id, "success");
        }

        String asrEngineName = connectParams == null ? null : connectParams.getString("AsrEngine");
        mrcpManage.updateConnection(id, null, asrEngineName);
        if (connectParams != null && connectParams.getString("TtsEngine") != null) {
            // 设置TTS引擎和发音人
            String ttsEngineName = connectParams.getString("TtsEngine");
            mrcpManage.setTtsEngineName(id, ttsEngineName);
            if (connectParams.getString("Voice") != null) {
                String voice = connectParams.getString("Voice");
                mrcpManage.setVoice(id, voice);
            }
            if (connectParams.getString("PushAsrRealtimeResult") != null) {
                // 设置是否开启实时asr结果推送
                Boolean pushAsrRealtimeResult = connectParams.getBoolean("PushAsrRealtimeResult");
                mrcpManage.setPushAsrRealtimeResult(id, pushAsrRealtimeResult);
            }
        }
        return TcpResponse.success(id, "success");
    }
}

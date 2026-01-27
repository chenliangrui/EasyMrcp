package com.cfsl.easymrcp.tcp.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.asr.AsrHandler;
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
        mrcpManage.updateConnection(id);
        JSONObject connectParams = JSON.parseObject(event.getData());
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
        if (connectParams != null && connectParams.getString("Type") != null && connectParams.getString("Type").equals("spy")) {
            mrcpManage.setPushAsrRealtimeResult(id, false);
            // 启动spy模式，对某一路通话进行asr识别
            SipContext sipContext = SpringUtils.getBean(SipContext.class);
            RtpManager rtpManager = SpringUtils.getBean(RtpManager.class);
            SipOptions sipOptions = SpringUtils.getBean(SipOptions.class);
            HandleSipInit handleSipInit = SpringUtils.getBean(HandleSipInit.class);
            AsrHandler asrHandler = handleSipInit.initAsr(sipOptions.getFsServerIp(), 0, 8, id);
            int rtpPort = sipContext.getAsrRtpPort();
            Channel rtpChannel = rtpManager.createRtpChannel(id, rtpPort, asrHandler.getNettyAsrRtpProcessor());
            JSONObject connectParamsRes = new JSONObject();
            connectParamsRes.put("rtpPort", rtpPort);
            tcpClientNotifier.sendEvent(id, null,TcpEventType.ClientConnect, connectParamsRes.toJSONString());
        }
        return TcpResponse.success(id, "success");
    }
}

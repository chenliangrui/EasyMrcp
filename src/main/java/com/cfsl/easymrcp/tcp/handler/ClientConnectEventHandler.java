package com.cfsl.easymrcp.tcp.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.common.ProcessorCreator;
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
            String ttsEngine = connectParams.getString("TtsEngine");
            mrcpManage.setTtsEngine(id, ttsEngine);
            if (connectParams.getString("Voice") != null) {
                String voice = connectParams.getString("Voice");
                mrcpManage.setVoice(id, voice);
            }
        }
        if (connectParams != null && connectParams.getString("Type") != null && connectParams.getString("Type").equals("spy")) {
            // 启动spy模式，对某一路通话进行asr识别
            SipContext sipContext = SpringUtils.getBean(SipContext.class);
            RtpManager rtpManager = SpringUtils.getBean(RtpManager.class);
            SipOptions sipOptions = SpringUtils.getBean(SipOptions.class);
            HandleSipInit handleSipInit = SpringUtils.getBean(HandleSipInit.class);
            AsrHandler asrHandler = handleSipInit.initAsr(sipOptions.getFsServerIp(), 0, id);
            int rtpPort = sipContext.getAsrRtpPort();
            Channel rtpChannel = rtpManager.createRtpChannel(id, rtpPort, asrHandler.getNettyAsrRtpProcessor());
            JSONObject connectParamsRes = new JSONObject();
            connectParamsRes.put("rtpPort", rtpPort);
            tcpClientNotifier.sendEvent(id, TcpEventType.ClientConnect, connectParamsRes.toJSONString());
        }
        return TcpResponse.success(id, "success");
    }
}

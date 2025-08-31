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
        if (connectParams.getString("Type") != null && connectParams.getString("Type").equals("spy")) {
            // 测试
            SipContext sipContext = SpringUtils.getBean(SipContext.class);
            ProcessorCreator asrChose = SpringUtils.getBean(ProcessorCreator.class);
            RtpManager rtpManager = SpringUtils.getBean(RtpManager.class);
            SipOptions sipOptions = SpringUtils.getBean(SipOptions.class);
            HandleSipInit handleSipInit = SpringUtils.getBean(HandleSipInit.class);
//            AsrHandler asrHandler = asrChose.getAsrHandler();
//            asrHandler.setChannelId("11111");
//            asrHandler.create(sipOptions.getFsServerIp(), 0);
//            asrHandler.receive();
//            mrcpManage.addNewAsr(id, asrHandler);
//            asrHandler.setInterruptEnable(mrcpManage.getInterruptEnable(id));
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

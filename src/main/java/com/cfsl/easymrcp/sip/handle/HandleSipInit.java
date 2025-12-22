package com.cfsl.easymrcp.sip.handle;

import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.common.ProcessorCreator;
import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.rtp.*;
import com.cfsl.easymrcp.sdp.SdpMessage;
import com.cfsl.easymrcp.sip.SipSession;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpEventType;
import com.cfsl.easymrcp.tts.TtsHandler;
import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.MediaDescription;
import java.net.InetAddress;
import java.util.List;
import java.util.Vector;

@Slf4j
@Service
public class HandleSipInit {
    @Autowired
    SipContext sipContext;
    @Autowired
    SipMrcpManage rtpManage;
    @Autowired
    MrcpManage mrcpManage;
    @Autowired
    ProcessorCreator asrChose;
    @Autowired
    SipUtils sipUtils;
    @Autowired
    TcpClientNotifier tcpClientNotifier;
    @Autowired
    RtpManager rtpManager;

    public SdpMessage initAsrAndTts(SdpMessage sdpMessage, SipSession session, String customHeaderUUID) {
        String dialogId = session.getDialog().getDialogId();
        log.debug("{} is a dialog", dialogId);
        log.debug("description: {}", sdpMessage.getSessionDescription());
        try {
            List<MediaDescription> channels = sdpMessage.getRtpChannels();
            if (!channels.isEmpty()) {
                for (MediaDescription md : channels) {
                    List<MediaDescription> rtpmd = sdpMessage.getAudioChansForThisControlChan(md);
                    Vector<String> formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true);
                    InetAddress remoteHost = InetAddress.getByName(sdpMessage.getSessionAddress());
                    int remotePort = rtpmd.get(0).getMedia().getMediaPort();
                    Vector<String> useProtocol = sipUtils.getSupportProtocols(formatsInRequest);
                    
                    // 解析选定的编码类型
                    int mediaType = 8; // 默认使用PCMA
                    if (!useProtocol.isEmpty()) {
                        mediaType = AudioCodecUtil.parsePayloadType(useProtocol.get(0));
                    }
                    
                    // 获取初始RTP端口
                    int rtpPort = sipContext.getAsrRtpPort();
                    log.debug("获取初始RTP端口: {}", rtpPort);
                    
                    try {
                        // 更新SDP媒体描述中的端口
                        rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
                        rtpmd.get(0).getMedia().setMediaPort(rtpPort);

                        // 初始化ASR，传递mediaType
                        AsrHandler asrHandler = initAsr(remoteHost.getHostAddress(), remotePort, mediaType, customHeaderUUID);
                        // 初始化TTS，传递mediaType
                        TtsHandler ttsHandler = initTts(rtpPort, remoteHost.getHostAddress(), remotePort, mediaType, customHeaderUUID);
                        // 建立rtp连接
                        Channel rtpChannel = rtpManager.createRtpChannel(dialogId, rtpPort, asrHandler.getNettyAsrRtpProcessor());
                        ttsHandler.setRtpChannel(rtpChannel);
                        ttsHandler.startRtpSender();
                        JSONObject connectParams = new JSONObject();
                        connectParams.put("msg", "SipInitSuccess");
                        tcpClientNotifier.sendEvent(customHeaderUUID, null,TcpEventType.ClientConnect, connectParams.toJSONString());
                    } catch (Exception e) {
                        log.error("初始化RTP通道失败", e);
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            log.error("初始化ASR和TTS失败", e);
        }
        rtpManage.addMrcpUuid(dialogId, customHeaderUUID);
        return sdpMessage;
    }

    public AsrHandler initAsr(String remoteHost, int remotePort, int mediaType, String customHeaderUUID) {
        try {
            AsrHandler asrHandler = asrChose.getAsrHandler();
            asrHandler.setCallId(customHeaderUUID);
            asrHandler.create(remoteHost, remotePort, mediaType);
            asrHandler.receive();
            // 向mrcp业务中写入asrHandler，此时已经明确callId，等待tcp连接发送uuid
            mrcpManage.addNewAsr(customHeaderUUID, asrHandler);
            asrHandler.setInterruptEnable(mrcpManage.getInterruptEnable(customHeaderUUID));
            asrHandler.setPushAsrRealtimeResult(mrcpManage.getPushAsrRealtimeResult(customHeaderUUID));
            return asrHandler;
        } catch (Exception e) {
            log.error("初始化ASR失败", e);
            throw new RuntimeException("初始化ASR失败", e);
        }
    }

    private TtsHandler initTts(int localPort, String remoteHost, int remotePort, int mediaType, String customHeaderUUID) {
        try {
            TtsHandler ttsHandler = asrChose.getTtsHandler();
            log.debug("初始化TTS，本地端口: {}, 远程地址: {}:{}, 编码类型: {}", 
                localPort, remoteHost, remotePort, AudioCodecUtil.getCodecName(mediaType));
            ttsHandler.create(remoteHost, remotePort, mediaType);
            ttsHandler.setCallId(customHeaderUUID);
            mrcpManage.addNewTts(customHeaderUUID, ttsHandler);
            return ttsHandler;
        } catch (Exception e) {
            log.error("初始化TTS失败", e);
            throw new RuntimeException("初始化TTS失败", e);
        }
    }
}

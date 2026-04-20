package com.cfsl.easymrcp.sip.handle;

import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.common.EMConstant;
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
import gov.nist.javax.sdp.fields.AttributeField;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.MediaDescription;
import javax.sdp.SdpParseException;
import java.net.InetAddress;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    HandleError handleError;

    public SdpMessage initAsrAndTts(SdpMessage sdpMessage, SipSession session, String customHeaderUUID) {
        String dialogId = session.getDialog().getDialogId();
        log.debug("{} is a dialog", dialogId);
        log.debug("description: {}", sdpMessage.getSessionDescription());
        try {
            List<MediaDescription> channels = sdpMessage.getRtpChannels();
            if (!channels.isEmpty()) {
                for (MediaDescription md : channels) {
                    List<MediaDescription> rtpmd = sdpMessage.getAudioChansForThisControlChan(md);
                    // 动态获取8khz PCM 16协议
                    Vector<AttributeField> attributes = rtpmd.get(0).getAttributes(true);
                    final String[] pt = {null};
                    attributes.forEach(attribute -> {
                        try {
                            if (attribute.getName().equals("rtpmap")) {
                                if (attribute.getAttribute().getValue().contains("L16/8000")) {
                                    pt[0] = attribute.getAttribute().getValue().replace("L16/8000", "").trim();
                                }
                            }
                        } catch (SdpParseException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    // 解析选定的编码类型
                    int mediaType = 8; // 默认使用PCMA
                    int sendIntervalMs = 20;
                    int frameBytes = EMConstant.VOIP_SAMPLES_PER_FRAME;
                    Vector<String> useProtocol;
                    Vector<String> formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true);
                    if (pt[0] != null) {
                        // 使用自动协商的8khz PCM 16编码
                        useProtocol = new Vector<>();
                        useProtocol.add(pt[0]);
                        mediaType = Integer.parseInt(pt[0]);
                    } else {
                        useProtocol = sipUtils.getSupportProtocols(formatsInRequest);
                        if (!useProtocol.isEmpty()) {
                            mediaType = AudioCodecUtil.parsePayloadType(useProtocol.get(0));
                        }
                    }

                    // 计算帧大小
                    for (AttributeField attribute : attributes) {
                        try {
                            if (attribute.getName().equals("ptime")) {
                                sendIntervalMs = Integer.parseInt(attribute.getValue().trim());
                            }
                        } catch (Exception e) {
                            log.warn("解析ptime失败，使用默认值20ms", e);
                        }
                    }

                    boolean isNegotiatedL16 = pt[0] != null && Integer.parseInt(pt[0]) == mediaType;
                    int bytesPerMs = isNegotiatedL16 ? (EMConstant.VOIP_SAMPLE_RATE * 2 / 1000) : (EMConstant.VOIP_SAMPLE_RATE / 1000);
                    frameBytes = bytesPerMs * sendIntervalMs;
                    InetAddress remoteHost = InetAddress.getByName(sdpMessage.getSessionAddress());
                    int remotePort = rtpmd.get(0).getMedia().getMediaPort();
                    
                    // 获取初始RTP端口
                    int rtpPort = sipContext.getAsrRtpPort();
                    log.debug("获取初始RTP端口: {}", rtpPort);

                    // mrcpManage检查有没有连接，没有则等待easymrcp client的连接
                    if (!mrcpManage.containsCallId(customHeaderUUID)) {
                        try {
                            CountDownLatch countDownLatch = new CountDownLatch(1);
                            mrcpManage.updateConnection(customHeaderUUID, countDownLatch);
                            boolean await = countDownLatch.await(30, TimeUnit.SECONDS);
                            if (!await) {
                                mrcpManage.removeMrcpCallData(customHeaderUUID);
                                throw new RuntimeException();
                            }
                        } catch (Exception e) {
                            mrcpManage.removeMrcpCallData(customHeaderUUID);
                            handleError.send486(session);
                            throw new RuntimeException("连接错误，超30秒EasyMrcp client仍然未连接，请检查client连接");
                        }
                    }
                    
                    try {
                        // 更新SDP媒体描述中的端口
                        rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
                        rtpmd.get(0).getMedia().setMediaPort(rtpPort);

                        // 初始化ASR，传递mediaType
                        AsrHandler asrHandler = initAsr(remoteHost.getHostAddress(), remotePort, mediaType, frameBytes, sendIntervalMs, customHeaderUUID);
                        // 初始化TTS，传递mediaType
                        TtsHandler ttsHandler = initTts(rtpPort, remoteHost.getHostAddress(), remotePort, mediaType, frameBytes, sendIntervalMs, customHeaderUUID);
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

    public AsrHandler initAsr(String remoteHost, int remotePort, int mediaType, int frameBytes, int sendIntervalMs, String customHeaderUUID) {
        try {
            AsrHandler asrHandler = asrChose.getAsrHandler();
            asrHandler.setCallId(customHeaderUUID);
            asrHandler.create(remoteHost, remotePort, mediaType, frameBytes, sendIntervalMs);
            asrHandler.receive();
            // 向mrcp业务中写入asrHandler，此时已经明确callId，等待tcp连接发送uuid
            mrcpManage.addNewAsr(customHeaderUUID, asrHandler);
            asrHandler.setInterruptEnable(mrcpManage.getInterruptEnable(customHeaderUUID));
            asrHandler.getPushAsrRealtimeResult().set(mrcpManage.getPushAsrRealtimeResult(customHeaderUUID) != null);
            return asrHandler;
        } catch (Exception e) {
            log.error("初始化ASR失败", e);
            throw new RuntimeException("初始化ASR失败", e);
        }
    }

    private TtsHandler initTts(int localPort, String remoteHost, int remotePort, int mediaType, int frameBytes, int sendIntervalMs, String customHeaderUUID) {
        try {
            TtsHandler ttsHandler = asrChose.getTtsHandler();
            log.debug("初始化TTS，本地端口: {}, 远程地址: {}:{}, 编码类型: {}", 
                localPort, remoteHost, remotePort, AudioCodecUtil.getCodecName(mediaType));
            ttsHandler.create(remoteHost, remotePort, mediaType, frameBytes, sendIntervalMs);
            ttsHandler.setCallId(customHeaderUUID);
            mrcpManage.addNewTts(customHeaderUUID, ttsHandler);
            return ttsHandler;
        } catch (Exception e) {
            log.error("初始化TTS失败", e);
            throw new RuntimeException("初始化TTS失败", e);
        }
    }
}

package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.common.ProcessorCreator;
import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.mrcp.MrcpTimeoutManager;
import com.cfsl.easymrcp.rtp.RtpConnection;
import com.cfsl.easymrcp.rtp.SipRtpManage;
import com.cfsl.easymrcp.rtp.RtpSession;
import com.cfsl.easymrcp.sdp.SdpMessage;
import com.cfsl.easymrcp.sip.MrcpServer;
import com.cfsl.easymrcp.sip.SipSession;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpEventType;
import com.cfsl.easymrcp.tts.TtsHandler;
import com.cfsl.easymrcp.utils.SipUtils;
import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpEventName;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.header.CompletionCause;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.server.MrcpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.MediaDescription;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Vector;

@Slf4j
@Service
public class HandleSipInit {
    @Autowired
    SipContext sipContext;
    @Autowired
    MrcpServer mrcpServer;
    @Autowired
    SipRtpManage rtpManage;
    @Autowired
    MrcpManage mrcpManage;
    @Autowired
    ProcessorCreator asrChose;
    @Autowired
    SipUtils sipUtils;
    @Autowired
    TcpClientNotifier tcpClientNotifier;

    public SdpMessage initAsrAndTts(SdpMessage sdpMessage, SipSession session, String customHeaderUUID) {
        String dialogId = session.getDialog().getDialogId();
        RtpSession rtpSession = new RtpSession(dialogId);
        log.info(dialogId + " is a dialog");
        log.info("description: " + sdpMessage.getSessionDescription());
        try {
            List<MediaDescription> channels = sdpMessage.getRtpChannels();
            if (!channels.isEmpty()) {
                for (MediaDescription md : channels) {
                    List<MediaDescription> rtpmd = sdpMessage.getAudioChansForThisControlChan(md);
                    Vector<String> formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true);
                    InetAddress remoteHost = InetAddress.getByName(sdpMessage.getSessionAddress());
                    int remotePort = rtpmd.get(0).getMedia().getMediaPort();
                    Vector<String> useProtocol = sipUtils.getSupportProtocols(formatsInRequest);
                    // 开启rtp通道
                    int asrRtpPort = sipContext.getAsrRtpPort();
                    // 处理rtp端口占用问题，检测到端口占用后自动加1重试
                    boolean startedRtp = false;
                    int findRtpCount = 0;
                    DatagramSocket datagramSocket = null;
                    do {
                        findRtpCount++;
                        try {
                            datagramSocket = new DatagramSocket(asrRtpPort);
                        } catch (Exception e) {
                            if (e instanceof BindException) {
                                log.error("asrRtpPort: " + asrRtpPort + " is already in use");
                                asrRtpPort = sipContext.getAsrRtpPort();
                                continue;
                            }
                        }
                        startedRtp = true;
                    } while (!startedRtp && findRtpCount <= 10);
                    rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
                    rtpmd.get(0).getMedia().setMediaPort(asrRtpPort);

                    initAsr(rtpSession, datagramSocket, customHeaderUUID);
                    initTts(remoteHost, remotePort, customHeaderUUID, rtpSession, datagramSocket);
                    tcpClientNotifier.sendEvent(customHeaderUUID, TcpEventType.ClientConnect, "SipInitSuccess");
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        rtpManage.addRtpSession(dialogId, rtpSession);
        return sdpMessage;
    }

    private void initAsr(RtpSession rtpSession, DatagramSocket datagramSocket, String customHeaderUUID) {
        try {
            AsrHandler asrHandler = asrChose.getAsrHandler();
            asrHandler.setChannelId("11111");
            asrHandler.create(null, datagramSocket, null, 0);
            asrHandler.receive();
            rtpSession.addChannel("11111", asrHandler);
            // 向mrcp业务中写入asrHandler，此时已经明确callId，等待tcp连接发送uuid
            mrcpManage.addNewAsr(customHeaderUUID, asrHandler);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void initTts(InetAddress remoteHost, int remotePort, String customHeaderUUID, RtpSession rtpSession, DatagramSocket datagramSocket) {
        try {
            Map<String, RtpConnection> channelMaps = rtpSession.getChannelMaps();
            // 开启rtp
            TtsHandler ttsHandler = asrChose.getTtsHandler();
            ttsHandler.create(null, datagramSocket, remoteHost.getHostAddress(), remotePort);
            ttsHandler.setChannelId("222222");
            channelMaps.put("222222", ttsHandler);
            mrcpManage.addNewTts(customHeaderUUID, ttsHandler);
            log.debug("Created a SPEECHSYNTH Channel.  id is: 222222"+" rtp remotehost:port is: "+ remoteHost.getHostAddress()+":"+remotePort);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}

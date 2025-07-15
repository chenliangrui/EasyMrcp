package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.common.ProcessorCreator;
import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.rtp.RtpConnection;
import com.cfsl.easymrcp.rtp.SipRtpManage;
import com.cfsl.easymrcp.rtp.RtpSession;
import com.cfsl.easymrcp.sdp.SdpMessage;
import com.cfsl.easymrcp.sip.MrcpServer;
import com.cfsl.easymrcp.sip.SipSession;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tts.TtsHandler;
import com.cfsl.easymrcp.utils.SipUtils;
import gov.nist.javax.sdp.fields.AttributeField;
import lombok.extern.slf4j.Slf4j;
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
public class HandleReceiver {
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

    public SdpMessage invite(SdpMessage sdpMessage, SipSession session, String customHeaderUUID) {
        String dialogId = session.getDialog().getDialogId();
        RtpSession rtpSession = new RtpSession(dialogId);
        log.info(dialogId + " is a dialog");
        log.info("description: " + sdpMessage.getSessionDescription());
        try {
            List<MediaDescription> channels = sdpMessage.getRtpChannels();
            if (channels.size() > 0) {
                for (MediaDescription md : channels) {
                    List<MediaDescription> rtpmd = sdpMessage.getAudioChansForThisControlChan(md);
                    Vector<String> formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true);
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

                    AsrHandler asrHandler = asrChose.getAsrHandler();
                    asrHandler.setChannelId("11111");
                    asrHandler.create(null, datagramSocket, null, 0);
                    asrHandler.receive();
                    rtpSession.addChannel("11111", asrHandler);
                    // 向mrcp业务中写入asrHandler，此时已经明确callId，等待tcp连接发送uuid
                    mrcpManage.addNewAsr(customHeaderUUID, asrHandler);
                    asrHandler.setCallback(new AsrCallback() {
                        @Override
                        public void apply(String msg) {
                            mrcpManage.interrupt(customHeaderUUID);
                            tcpClientNotifier.sendAsrResultNotify(customHeaderUUID, msg);
                        }
                    });
                    // 开启mrcp通道
//                    mrcpServer.getMrcpServerSocket().openChannel("11111", new MrcpRecogChannel(asrHandler, mrcpManage));
                    md.getMedia().setMediaPort(mrcpServer.getMrcpServerSocket().getPort());
                    rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
                    rtpmd.get(0).getMedia().setMediaPort(asrRtpPort);
                    //修改sdp收发问题
                    for (Object attribute : rtpmd.get(0).getAttributes(true)) {
                        AttributeField attribute1 = (AttributeField) attribute;
                        if (attribute1.getName().equalsIgnoreCase("sendonly")) {
                            attribute1.setName("recvonly");
                        }
                    }

                    // TODO 测试
                    InetAddress remoteHost = InetAddress.getByName(sdpMessage.getSessionAddress());
                    tts(remoteHost, remotePort, customHeaderUUID, rtpmd, useProtocol, rtpSession, md, asrRtpPort, datagramSocket);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        rtpManage.addRtpSession(dialogId, rtpSession);
        return sdpMessage;
    }
    
    private void tts(InetAddress remoteHost, int remotePort, String customHeaderUUID, List<MediaDescription> rtpmd,
                     Vector<String> useProtocol, RtpSession rtpSession, MediaDescription md, int localPort, DatagramSocket datagramSocket) {
        try {
            Map<String, RtpConnection> channelMaps = rtpSession.getChannelMaps();
//            int localPort = sipContext.getTtsRtpPort();
            // 处理rtp端口占用问题，检测到端口占用后自动加1重试
//            boolean startedRtp = false;
//            int findRtpCount = 0;
//            DatagramSocket datagramSocket = null;
//            do {
//                findRtpCount++;
//                try {
//                    datagramSocket = new DatagramSocket(localPort);
//                } catch (Exception e) {
//                    if (e instanceof BindException) {
//                        log.error("ttsRtpPort: " + localPort + " is already in use");
//                        localPort = sipContext.getTtsRtpPort();
//                        continue;
//                    }
//                }
//                startedRtp = true;
//            } while (!startedRtp && findRtpCount <= 10);
            // 开启rtp
            TtsHandler ttsHandler = asrChose.getTtsHandler();
            ttsHandler.create(null, datagramSocket, remoteHost.getHostAddress(), remotePort);
//        ttsHandler.setChannelId(channelID);
            channelMaps.put("111111", ttsHandler);
            mrcpManage.addNewTts(customHeaderUUID, ttsHandler);
            // 开启mrcp
//                            mrcpServer.getMrcpServerSocket().openChannel(channelID, new MrcpSpeechSynthChannel(ttsHandler, mrcpManage));
            md.getMedia().setMediaPort(mrcpServer.getMrcpServerSocket().getPort());
            rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
            rtpmd.get(0).getMedia().setMediaPort(localPort);
            //修改sdp收发问题
            for (Object attribute : rtpmd.get(0).getAttributes(true)) {
                AttributeField attribute1 = (AttributeField) attribute;
                if (attribute1.getName().equalsIgnoreCase("recvonly")) {
                    attribute1.setName("sendonly");
                }
            }

            log.debug("Created a SPEECHSYNTH Channel.  id is: 111111"+" rtp remotehost:port is: "+ remoteHost.getHostAddress()+":"+remotePort);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

//    public SdpMessage invite(SdpMessage sdpMessage, SipSession session) {
//        String dialogId = session.getDialog().getDialogId();
//        RtpSession rtpSession = new RtpSession(dialogId);
//        log.info("description: " + sdpMessage.getSessionDescription());
//        try {
//            List<MediaDescription> channels = sdpMessage.getMrcpReceiverChannels();
//            if (channels.size() > 0) {
//                for(MediaDescription md: channels) {
//                    String channelID = md.getAttribute(SdpMessage.SDP_CHANNEL_ATTR_NAME);
//                    String rt =  md.getAttribute(SdpMessage.SDP_RESOURCE_ATTR_NAME);
//                    MrcpResourceType resourceType = null;
//                    log.debug("Resource Type: " +rt);
//                    if (rt.equalsIgnoreCase("speechrecog")) {
//                        resourceType = MrcpResourceType.SPEECHRECOG;
//                    } else if (rt.equalsIgnoreCase("speechsynth")) {
//                        resourceType = MrcpResourceType.SPEECHSYNTH;
//                    } else if (rt.equalsIgnoreCase("recorder")) {
//                        resourceType = MrcpResourceType.RECORDER;
//                    }
//                    List<MediaDescription> rtpmd = null;
//                    switch (resourceType) {
//                        case SPEECHRECOG:
//                            rtpmd = sdpMessage.getAudioChansForThisControlChan(md);
//                            Vector<String> formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true);
//                            Vector<String> useProtocol = sipUtils.getSupportProtocols(formatsInRequest);
//                            // 开启rtp通道
//                            int asrRtpPort = sipContext.getAsrRtpPort();
//                            // 处理rtp端口占用问题，检测到端口占用后自动加1重试
//                            boolean startedRtp = false;
//                            int findRtpCount = 0;
//                            DatagramSocket datagramSocket = null;
//                            do {
//                                findRtpCount++;
//                                try {
//                                    datagramSocket = new DatagramSocket(asrRtpPort);
//                                } catch (Exception e) {
//                                    if (e instanceof BindException) {
//                                        log.error("asrRtpPort: " + asrRtpPort + " is already in use");
//                                        asrRtpPort = sipContext.getAsrRtpPort();
//                                        continue;
//                                    }
//                                }
//                                startedRtp = true;
//                            } while (!startedRtp && findRtpCount <= 10);
//
//                            AsrHandler asrHandler = asrChose.getAsrHandler();
//                            asrHandler.setChannelId(channelID);
//                            asrHandler.create(null, datagramSocket,null, 0);
//                            asrHandler.receive();
//                            rtpSession.addChannel(channelID, asrHandler);
//                            // 开启mrcp通道
//                            mrcpServer.getMrcpServerSocket().openChannel(channelID, new MrcpRecogChannel(asrHandler, mrcpManage));
//                            md.getMedia().setMediaPort(mrcpServer.getMrcpServerSocket().getPort());
//                            rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
//                            rtpmd.get(0).getMedia().setMediaPort(asrRtpPort);
//                            //修改sdp收发问题
//                            for (Object attribute : rtpmd.get(0).getAttributes(true)) {
//                                AttributeField attribute1 = (AttributeField) attribute;
//                                if (attribute1.getName().equalsIgnoreCase("sendonly")) {
//                                    attribute1.setName("recvonly");
//                                }
//                            }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            log.error(e.getMessage(), e);
//        }
//        rtpManage.addRtpSession(dialogId, rtpSession);
//        return sdpMessage;
//    }
}

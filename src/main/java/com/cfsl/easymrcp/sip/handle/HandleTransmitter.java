package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.ProcessorCreator;
import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.rtp.*;
import com.cfsl.easymrcp.sdp.SdpMessage;
import com.cfsl.easymrcp.sip.MrcpServer;
import com.cfsl.easymrcp.sip.SipSession;
import com.cfsl.easymrcp.tts.TtsHandler;
import com.cfsl.easymrcp.utils.SipUtils;
import gov.nist.javax.sdp.fields.AttributeField;
import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Vector;

@Slf4j
@Service
public class HandleTransmitter {
    @Autowired
    SipContext sipContext;
    @Autowired
    SipMrcpManage rtpManage;
    @Autowired
    MrcpServer mrcpServer;
    @Autowired
    MrcpManage mrcpManage;
    @Autowired
    ProcessorCreator processorCreator;
    @Autowired
    SipUtils sipUtils;
    @Autowired
    RtpManager rtpManager;

    public SdpMessage invite(SdpMessage sdpMessage, SipSession session, String customHeaderUUID) {
        log.debug("transmitter initAsrAndTts for");
        log.debug(sdpMessage.getSessionDescription().toString());
        InetAddress remoteHost = null;

        // Create a resource session object
        // TODO: Check if there is already a session (ie. This is a re-initAsrAndTts)
//        RtpSession rtpSession = new RtpSession(session.getDialog().getDialogId());

        // get the map that holds list of the channels and the resources used for each channel
        // the key is the dialogID
//        Map<String, MrcpConnection> channelMaps = rtpSession.getChannelMaps();

        try {
            List<MediaDescription> channels = sdpMessage.getMrcpTransmitterChannels();
            if (channels.size() > 0) {
                remoteHost = InetAddress.getByName(sdpMessage.getSessionAddress());
                InetAddress mediaHost = remoteHost;
                int localPort = 0;
                int remotePort = 0;
                Vector useProtocol = null;
                for (MediaDescription md : channels) {
                    String channelID = md.getAttribute(SdpMessage.SDP_CHANNEL_ATTR_NAME);
                    String rt = md.getAttribute(SdpMessage.SDP_RESOURCE_ATTR_NAME);

                    MrcpResourceType resourceType = MrcpResourceType.fromString(rt);

                    List<MediaDescription> rtpmd = sdpMessage.getAudioChansForThisControlChan(md);
                    if (rtpmd.size() > 0) {
                        //TODO: Complete the method below that checks if audio format is supported.  
                        //      If not resource not available exception should be shown.
                        //      maybe this could be part of the up-front validation
                        Vector<String> formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true);
                        useProtocol = sipUtils.getSupportProtocols(formatsInRequest);

                        // TODO: What if there is more than 1 media channels?

                        // TODO: check if there is an override for the host attribute in the m block
                        // InetAddress remoteHost = InetAddress.getByName(rtpmd.get(1).getAttribute();
                        remotePort = rtpmd.get(0).getMedia().getMediaPort();

                        //get the host for the rtp channel.  maybe the media is going to a different host.
                        //if so there will be a c-line in the media block
                        if (rtpmd.get(0).getConnection()!= null)
                            mediaHost = InetAddress.getByName(rtpmd.get(0).getConnection().getAddress());

                    } else {
                        log.warn("No Media channel specified in the initAsrAndTts request");
                        // TODO: handle no media channel in the request corresponding to the mrcp channel (sip error)
                    }

                    switch (resourceType) {
                        case BASICSYNTH:
                        case SPEECHSYNTH:
                            localPort = sipContext.getTtsRtpPort();
                            // 处理rtp端口占用问题，检测到端口占用后自动加1重试
                            boolean startedRtp = false;
                            int findRtpCount = 0;
                            DatagramSocket datagramSocket = null;
                            do {
                                findRtpCount++;
                                try {
                                    datagramSocket = new DatagramSocket(localPort);
                                } catch (Exception e) {
                                    if (e instanceof BindException) {
                                        log.error("ttsRtpPort: " + localPort + " is already in use");
                                        localPort = sipContext.getTtsRtpPort();
                                        continue;
                                    }
                                }
                                startedRtp = true;
                            } while (!startedRtp && findRtpCount <= 10);
                            // 开启rtp
                            TtsHandler ttsHandler = processorCreator.getTtsHandler();
                            
//                            ttsHandler.create(null, datagramSocket, remoteHost.getHostAddress(), remotePort);
                            ttsHandler.setChannelId(channelID);
//                            channelMaps.put(channelID, ttsHandler);
                            mrcpManage.addNewTts(customHeaderUUID, ttsHandler);
                            // 开启mrcp
//                            mrcpServer.getMrcpServerSocket().openChannel(channelID, new MrcpSpeechSynthChannel(ttsHandler, mrcpManage));
                            md.getMedia().setMediaPort(mrcpServer.getMrcpServerSocket().getPort());

                            try {
                                AttributeField af = new AttributeField();
                                af.setName(SdpMessage.SDP_SETUP_ATTR_NAME);
                                af.setValue(SdpMessage.SDP_PASSIVE_SETUP);
                                md.addAttribute(af);

                                af = new AttributeField();
                                af.setName(SdpMessage.SDP_CONNECTION_ATTR_NAME);
                                af.setValue(SdpMessage.SDP_NEW_CONNECTION);
                                md.addAttribute(af);
                            } catch (SdpException e) {
                                log.error("SdpException when adding attribute",e);
                            }
                            
                            // 设置RTP相关信息
                            rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
                            rtpmd.get(0).getMedia().setMediaPort(localPort);
                            
                            // 修改sdp收发问题
                            for (Object attribute : rtpmd.get(0).getAttributes(true)) {
                                AttributeField attribute1 = (AttributeField) attribute;
                                if (attribute1.getName().equalsIgnoreCase("recvonly")) {
                                    attribute1.setName("sendonly");
                                }
                            }
                            
                            log.debug("Created a SPEECHSYNTH Channel. id is: "+channelID+" rtp remotehost:port is: "+ mediaHost+":"+remotePort);
                            break;
                            
                        default:
                            log.warn("No handler for resource " + resourceType);
                    }
                }
            } else {
                log.warn("Invite request had no channels.");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
//        rtpManage.addMrcpUuid(session.getDialog().getDialogId(), rtpSession);

        return sdpMessage;
    }
}

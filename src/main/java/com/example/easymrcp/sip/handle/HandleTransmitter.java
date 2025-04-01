package com.example.easymrcp.sip.handle;

import com.example.easymrcp.common.ProcessorCreator;
import com.example.easymrcp.mrcp.MrcpSpeechSynthChannel;
import com.example.easymrcp.rtp.*;
import com.example.easymrcp.sdp.SdpMessage;
import com.example.easymrcp.sip.MrcpServer;
import com.example.easymrcp.sip.SipSession;
import com.example.easymrcp.tts.TtsHandler;
import com.example.easymrcp.utils.SipUtils;
import gov.nist.javax.sdp.fields.AttributeField;
import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

@Slf4j
@Service
public class HandleTransmitter {

    @Autowired
    SipRtpManage rtpManage;
    @Autowired
    MrcpServer mrcpServer;
    @Autowired
    ProcessorCreator processorCreator;
    @Autowired
    SipUtils sipUtils;


    public SdpMessage invite(SdpMessage sdpMessage, SipSession session) {
        log.debug("transmitter invite for");
        log.debug(sdpMessage.getSessionDescription().toString());
        InetAddress remoteHost = null;

        // Create a resource session object
        // TODO: Check if there is already a session (ie. This is a re-invite)        
        RtpSession rtpSession = new RtpSession(session.getDialog().getDialogId());

        // get the map that holds list of the channels and the resources used for each channel
        // the key is the dialogID
        Map<String, RtpConnection> channelMaps = rtpSession.getChannelMaps();

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

                    // if (rt.equalsIgnoreCase("speechrecog")) {
                    // resourceType = MrcpResourceType.SPEECHRECOG;
                    // } else if (rt.equalsIgnoreCase("speechsynth")) {
                    // resourceType = MrcpResourceType.SPEECHSYNTH;
                    // }
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
                        log.warn("No Media channel specified in the invite request");
                        // TODO: handle no media channel in the request corresponding to the mrcp channel (sip error)
                    }

                    switch (resourceType) {
                        case BASICSYNTH:
                        case SPEECHSYNTH:
                            // 开启rtp
                            TtsHandler ttsHandler = processorCreator.getTtsHandler();
                            ttsHandler.create(remoteHost.getHostAddress(), remotePort);
                            ttsHandler.setChannelId(channelID);
                            channelMaps.put(channelID, ttsHandler);
                            // 开启mrcp
                            mrcpServer.getMrcpServerSocket().openChannel(channelID, new MrcpSpeechSynthChannel(ttsHandler));
                            md.getMedia().setMediaPort(mrcpServer.getMrcpServerSocket().getPort());
                            rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
                            rtpmd.get(0).getMedia().setMediaPort(5006);
                            //修改sdp收发问题
                            for (Object attribute : rtpmd.get(0).getAttributes(true)) {
                                AttributeField attribute1 = (AttributeField) attribute;
                                if (attribute1.getName().equalsIgnoreCase("recvonly")) {
                                    attribute1.setName("sendonly");
                                }
                            }

                            log.debug("Created a SPEECHSYNTH Channel.  id is: "+channelID+" rtp remotehost:port is: "+ mediaHost+":"+remotePort);
                            break;

                        default:
                            throw new RuntimeException("Unsupported resource type!");
                    }

                    // Create a channel resources object and put it in the channel map (which is in the session).  
                    // These resources must be returned to the pool when the channel is closed.  In the case of a 
                    // transmitter, the resource is the RTP port in the port pair pool
                    // TODO:  The channels should cleanup after themselves (retrun resource to pools)
                    //        instead of keeping track of the resoruces in the session.
//                    RtpTransmitter rtpTransmitter = new RtpTransmitter();
//                    rtpTransmitter.setChannelId(channelID);
//                    channelMaps.put(channelID, rtpTransmitter);
                }
            } else {
                log.warn("Invite request had no channels.");
            }
        } catch (SdpException | UnknownHostException e) {
            throw new RuntimeException(e);
        }
        // Add the session to the session list
        rtpManage.addRtpSession(session.getDialog().getDialogId(), rtpSession);

        return sdpMessage;
    }
}

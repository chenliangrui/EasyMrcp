package com.example.easymrcp.sip.handle;

import com.example.easymrcp.common.SipContext;
import com.example.easymrcp.mrcp.MrcpRecogChannel;
import com.example.easymrcp.mrcp.RtpReceiver;
import com.example.easymrcp.sdp.SdpMessage;
import com.example.easymrcp.sip.MrcpServer;
import com.example.easymrcp.sip.SipSession;
import gov.nist.javax.sdp.fields.AttributeField;
import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

@Slf4j
@Service
public class HandleReceiver {
    @Autowired
    SipContext sipContext;
    @Autowired
    MrcpServer mrcpServer;
    @Autowired
    MrcpRecogChannel mrcpRecogChannel;

    public SdpMessage invite(SdpMessage sdpMessage, SipSession session) {
        String dialogId = session.getDialog().getDialogId();
        log.info("description: " + sdpMessage.getSessionDescription());
        try {
            List<MediaDescription> channels = sdpMessage.getMrcpReceiverChannels();
            Vector formatsInRequest = null;
            if (channels.size() > 0) {
                for(MediaDescription md: channels) {
                    String channelID = md.getAttribute(SdpMessage.SDP_CHANNEL_ATTR_NAME);
                    String rt =  md.getAttribute(SdpMessage.SDP_RESOURCE_ATTR_NAME);
                    MrcpResourceType resourceType = null;
                    log.debug("Resource Type: " +rt);
                    if (rt.equalsIgnoreCase("speechrecog")) {
                        resourceType = MrcpResourceType.SPEECHRECOG;
                    } else if (rt.equalsIgnoreCase("speechsynth")) {
                        resourceType = MrcpResourceType.SPEECHSYNTH;
                    } else if (rt.equalsIgnoreCase("recorder")) {
                        resourceType = MrcpResourceType.RECORDER;
                    }
                    List<MediaDescription> rtpmd = null;
                    switch (resourceType) {
                        case SPEECHRECOG:
                            rtpmd = sdpMessage.getAudioChansForThisControlChan(md);
                            formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true);
                            // 工具类
                            Vector<String> useProtocol = new Vector<>();
                            for (String supportProtocol : sipContext.getSupportProtocols()) {
                                if (formatsInRequest.contains(supportProtocol)) {
                                    useProtocol.add(supportProtocol);
                                    break;
                                }
                            }
                            //TODO 开启mrcp通道
                            mrcpServer.getMrcpServerSocket().openChannel(channelID, mrcpRecogChannel);
                            md.getMedia().setMediaPort(mrcpServer.getMrcpServerSocket().getPort());
                            rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
                            //TODO 判断使用哪些协议
                            //TODO 创建RTP通道
                            RtpReceiver rtp = new RtpReceiver();
                            rtp.run();
                            rtpmd.get(0).getMedia().setMediaPort(5004);
                            //修改sdp收发问题
                            for (Object attribute : rtpmd.get(0).getAttributes(true)) {
                                AttributeField attribute1 = (AttributeField) attribute;
                                if (attribute1.getName().equalsIgnoreCase("sendonly")) {
                                    attribute1.setName("recvonly");
                                }
                            }
                    }
                }
            }
        } catch (SdpException e) {
            throw new RuntimeException(e);
        }
        return sdpMessage;
    }
}

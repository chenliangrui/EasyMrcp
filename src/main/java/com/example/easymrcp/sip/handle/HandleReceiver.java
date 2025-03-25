package com.example.easymrcp.sip.handle;

import com.example.easymrcp.asr.AsrHandler;
import com.example.easymrcp.common.ProcessorCreator;
import com.example.easymrcp.common.SipContext;
import com.example.easymrcp.mrcp.MrcpRecogChannel;
import com.example.easymrcp.rtp.SipRtpManage;
import com.example.easymrcp.rtp.RtpSession;
import com.example.easymrcp.sdp.SdpMessage;
import com.example.easymrcp.sip.MrcpServer;
import com.example.easymrcp.sip.SipSession;
import com.example.easymrcp.utils.SipUtils;
import gov.nist.javax.sdp.fields.AttributeField;
import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
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
    SipRtpManage rtpManage;
    @Autowired
    ProcessorCreator asrChose;
    @Autowired
    SipUtils sipUtils;

    public SdpMessage invite(SdpMessage sdpMessage, SipSession session) {
        String dialogId = session.getDialog().getDialogId();
        RtpSession rtpSession = new RtpSession(dialogId);
        log.info("description: " + sdpMessage.getSessionDescription());
        try {
            List<MediaDescription> channels = sdpMessage.getMrcpReceiverChannels();
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
                            Vector<String> formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true);
                            Vector<String> useProtocol = sipUtils.getSupportProtocols(formatsInRequest);
                            // 开启rtp通道
                            AsrHandler asrHandler = asrChose.getAsrHandler();
                            asrHandler.setChannelId(channelID);
                            //TODO 等待asr连接成功
                            asrHandler.create(null,0);
                            asrHandler.receive();
                            rtpSession.addChannel(channelID, asrHandler);
                            // 开启mrcp通道
                            mrcpServer.getMrcpServerSocket().openChannel(channelID, new MrcpRecogChannel(asrHandler));
                            md.getMedia().setMediaPort(mrcpServer.getMrcpServerSocket().getPort());
                            rtpmd.get(0).getMedia().setMediaFormats(useProtocol);
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
        rtpManage.addRtpSession(dialogId, rtpSession);
        return sdpMessage;
    }
}

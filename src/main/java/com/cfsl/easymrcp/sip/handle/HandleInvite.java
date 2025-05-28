package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sdp.SdpMessage;
import com.cfsl.easymrcp.sip.SipManage;
import com.cfsl.easymrcp.sip.SipSession;
import com.cfsl.easymrcp.utils.SipUtils;
import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.*;
import javax.sip.*;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;

@Slf4j
@Service
public class HandleInvite {
    @Autowired
    private SipContext sipContext;
    @Autowired
    SipManage sipManage;
    @Autowired
    HandleReceiver handleReceiver;
    @Autowired
    HandleTransmitter handleTransmitter;
    @Autowired
    HandleOk handleOk;

    private long _channelID = System.currentTimeMillis();

    public void handleInvite(RequestEvent requestEvent) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        String guid = SipUtils.getGUID();
        SipSession sipSession = null;
        try {
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                // 解决freeswitch没有Contact问题
                request.setHeader(sipContext.getContactHeader("xiaohua", "1.1.1.1"));
                st = sipProvider.getNewServerTransaction(request);
            }

            byte[] rawContent = request.getRawContent();
            SdpFactory sdpFactory = SdpFactory.getInstance();
            if (rawContent == null) {
                log.warn("no offer in invite request");
            } else {
                Dialog dialog = requestEvent.getDialog();
                if (dialog == null) {
                    // sending TRYING 导致没有DialogID
                    Response response = sipContext.getMessageFactory().createResponse(Response.RINGING, request);
                    ToHeader provToHeader = (ToHeader) response.getHeader(ToHeader.NAME);
                    provToHeader.setTag(guid);
                    try {
                        st.sendResponse(response);
                    } catch (SipException | InvalidArgumentException e) {
                        log.error("send Trying error:{}", e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                    dialog = st.getDialog();
                    if (dialog != null && sipManage.hasSipSession(dialog.getDialogId())) {
                        // TODO handle re-invite
                        log.info("Receive re-invite, please consider handling it");
                    } else {
                        sipSession = new SipSession();
                        sipSession.setDialog(dialog);
                        sipSession.setStx(st);
                        sipSession.setRequestEvent(requestEvent);
                        sipManage.addSipSession(sipSession);
                    }
                }
                String contentString = new String(rawContent);
                SessionDescription sessionDescription = sdpFactory.createSessionDescription(contentString);
                SdpMessage sdpSessionMessage = SdpMessage.createSdpSessionMessage(sessionDescription);
                SdpMessage invite = invite(sdpSessionMessage, sipSession);
                try {
                    handleOk.sendResponse(sipSession, invite);
                } catch (SipException e) {
                    log.warn("error processing bye: " + e.getMessage(), e);
                    throw new SdpException(e.getMessage(), e);
                }
            }
        } catch (TransactionAlreadyExistsException | TransactionUnavailableException | ParseException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (SdpException e) {
            throw new RuntimeException(e);
        }


    }

    private SdpMessage invite(SdpMessage sdpMessage, SipSession session) throws SdpException {
        boolean receiver = false;
        boolean transmitter = false;
        try {
            for (MediaDescription md : sdpMessage.getMrcpReceiverChannels()) {
                String channelID = getNextChannelID();
                String chanid = channelID + '@' + MrcpResourceType.SPEECHRECOG.toString();
                md.setAttribute("channel", chanid);
                md.setAttribute("setup", "passive");
                receiver = true;
            }
            for (MediaDescription md : sdpMessage.getMrcpRecorderChannels()) {
                String channelID = getNextChannelID();
                String chanid = channelID + '@' + MrcpResourceType.RECORDER.toString();
                md.setAttribute("channel", chanid);
                md.setAttribute("setup", "passive");
                receiver = true;
            }
            for (MediaDescription md : sdpMessage.getMrcpTransmitterChannels()) {
                String channelID = getNextChannelID();
                String chanid = channelID + '@' + MrcpResourceType.SPEECHSYNTH.toString();
                md.setAttribute("channel", chanid);
                md.setAttribute("setup", "passive");
                transmitter = true;
            }
        } catch (SdpException e) {
            log.warn(e.getMessage(), e);
        }
        if (transmitter) {
            sdpMessage = handleTransmitter.invite(sdpMessage, session);
        }
        if (receiver) {
            sdpMessage = handleReceiver.invite(sdpMessage, session);
        }
        for (MediaDescription md : sdpMessage.getMrcpChannels()) {
            md.removeAttribute("resource");
        }
        sdpMessage.getSessionDescription().getConnection().setAddress(sipContext.getSipServerIp());
        return sdpMessage;
    }


    private synchronized String getNextChannelID() { // TODO: convert from synchronized to atomic
        return Long.toHexString(_channelID++);
    }
}

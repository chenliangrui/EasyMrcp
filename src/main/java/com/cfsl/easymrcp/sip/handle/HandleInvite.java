package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sdp.SdpMessage;
import com.cfsl.easymrcp.sip.SipManage;
import com.cfsl.easymrcp.sip.SipSession;
import com.cfsl.easymrcp.utils.SipUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.*;
import javax.sip.*;
import javax.sip.header.ExtensionHeader;
import javax.sip.header.Header;
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
    HandleSipInit handleSipInit;
    @Autowired
    HandleTransmitter handleTransmitter;
    @Autowired
    HandleOk handleOk;

    public void handleInvite(RequestEvent requestEvent) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        String guid = SipUtils.getGUID();
        SipSession sipSession = null;
        
        // 解析自定义头部 X-EasyMRCP
        String customHeaderUUID = null;
        Header customHeader = request.getHeader("X-EasyMRCP");
        if (customHeader != null) {
            if (customHeader instanceof ExtensionHeader) {
                customHeaderUUID = ((ExtensionHeader) customHeader).getValue();
            } else {
                customHeaderUUID = customHeader.toString().substring(customHeader.toString().indexOf(":") + 1).trim();
            }
            log.info("Received X-EasyMRCP with value: {}", customHeaderUUID);
        }
        
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
                log.warn("no offer in initAsrAndTts request");
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
                        // TODO handle re-initAsrAndTts
                        log.info("Receive re-initAsrAndTts, please consider handling it");
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
                SdpMessage invite = invite(sdpSessionMessage, sipSession, customHeaderUUID);
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

    private SdpMessage invite(SdpMessage sdpMessage, SipSession session, String customHeaderUUID) throws SdpException {
        sdpMessage = handleSipInit.initAsrAndTts(sdpMessage, session, customHeaderUUID);
        sdpMessage.getSessionDescription().getConnection().setAddress(sipContext.getSipServerIp());
        return sdpMessage;
    }
}

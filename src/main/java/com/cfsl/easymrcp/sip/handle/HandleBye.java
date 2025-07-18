package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.rtp.RtpConnection;
import com.cfsl.easymrcp.rtp.SipRtpManage;
import com.cfsl.easymrcp.rtp.RtpSession;
import com.cfsl.easymrcp.sip.MrcpServer;
import com.cfsl.easymrcp.sip.SipManage;
import com.cfsl.easymrcp.sip.SipSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sip.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Service
public class HandleBye {
    @Autowired
    SipContext sipContext;
    @Autowired
    SipManage sipManage;
    @Autowired
    SipRtpManage rtpManage;
    @Autowired
    MrcpServer mrcpServer;

    public void processBye(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        ServerTransaction stx = requestEvent.getServerTransaction();
        Dialog dialog = requestEvent.getDialog();
        SipSession session = sipManage.getSipSession(dialog.getDialogId());

        // TODO: check for any pending requests. The spec says that the
        // "UAS MUST still respond to any pending requests received for that
        // dialog. It is RECOMMENDED that a 487 (Request Terminated) response
        // be generated to those pending requests."
        if (session == null) {
            log.info("Receieved a BYE for which there is no corresponding session.  SessionID: "+dialog.getDialogId());
        } else {
            try {
                //process the invitaion (the resource manager processInviteRequest method)
                bye(session.getDialog().getDialogId());
                sipManage.removeSipSession(session.getDialog().getDialogId());
                Response response = sipContext.getMessageFactory().createResponse(200, request);
                sendResponse(stx, response);
            } catch (ParseException e) {
                log.error("Error parsing BYE request: {}", e.getMessage(), e);
            } catch (SipException | RemoteException e) {
                log.error("Error sending BYE response: {}", e.getMessage(), e);
            } catch (InvalidArgumentException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void bye(String sessionId) throws RemoteException {
        RtpSession rtpSession = rtpManage.getRtpSession(sessionId);
        Map<String, RtpConnection> channelMaps = rtpSession.getChannelMaps();
        for(RtpConnection channel: channelMaps.values()) {
//            mrcpServer.getMrcpServerSocket().closeChannel(channel.getChannelId());
            try {
                channel.close();
            } catch (Exception e) {
                log.error("close rtp channel failed", e);
            }
        }
        rtpManage.removeRtpSession(sessionId);
    }

    public static void sendResponse(ServerTransaction serverTransaction, Response response) throws SipException, InvalidArgumentException {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("------------- SENDING A SIP RESPONSE ---------------");
            sb.append("\nSending a SIP Response.  Status: "+response.getStatusCode()+", "+response.getReasonPhrase());
            Iterator headers = response.getHeaderNames();
            while (headers.hasNext()) {
                sb.append("\n");
                sb.append(response.getHeader((String) headers.next()).toString());
            }
            byte[] contentBytes = response.getRawContent();
            if (contentBytes == null) {
                sb.append("\nNo content in the response.");
            } else {
                sb.append("\n");
                String contentString = new String(contentBytes);
                sb.append(contentString);
            }
            log.debug(sb.toString());
        }
        serverTransaction.sendResponse(response);
    }
}

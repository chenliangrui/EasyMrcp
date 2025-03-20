package com.example.easymrcp.sip.handle;

import com.example.easymrcp.common.SipContext;
import com.example.easymrcp.sdp.SdpMessage;
import com.example.easymrcp.sip.SipSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.Iterator;

@Slf4j
@Service
public class HandleOk {
    @Autowired
    SipContext sipContext;

    public void sendResponse(SipSession session, SdpMessage sdpResponse) throws SipException {
        // send the ok (assuming that the offer is accepted with the response in the sdpMessaage)
        //TODO what if the offer is not accepted?  Do all non-ok response come thru the exception path?
        Response okResponse = null;
        try {
            okResponse = sipContext.getMessageFactory().createResponse(Response.OK, session.getRequestEvent().getRequest());
        } catch (ParseException e) {
            log.warn("error creating OK response", e);
            throw new SipException("error creating OK response", e);
        }

        // Create a application/sdp ContentTypeHeader
        ContentTypeHeader contentTypeHeader = null;
        try {
            contentTypeHeader = sipContext.getHeaderFactory().createContentTypeHeader("application", "sdp");
        } catch (ParseException e) {
            log.warn("error creating SDP header", e);
            throw new SipException("error creating OK response", e);
        }

        // add the SDP response to the message
        try {
            okResponse.setContent(sdpResponse.getSessionDescription().toString(), contentTypeHeader);
        } catch (ParseException e) {
            log.warn("error setting SDP header in OK response", e);
            throw new SipException("error creating OK response", e);
        }

        ToHeader toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
        //toHeader.setTag(guid);

        okResponse.addHeader(sipContext.getContactHeader());

        // Now if there were no exceptions, we were able to process the invite
        // request and we have a valid response to send back
        // if there is an exception here, not much that can be done.
        try {
            sendResponse(session.getStx(), okResponse);
        } catch (InvalidArgumentException e) {
            log.error(e.getMessage(), e);
            throw new SipException(e.getMessage(), e);
        }
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

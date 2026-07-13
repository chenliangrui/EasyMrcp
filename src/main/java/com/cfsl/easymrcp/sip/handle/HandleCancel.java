package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sip.SipManage;
import com.cfsl.easymrcp.sip.SipSession;
import gov.nist.javax.sip.stack.SIPServerTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sip.Dialog;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;

@Slf4j
@Service
public class HandleCancel {

    @Autowired
    SipContext sipContext;

    @Autowired
    SipManage sipManage;

    public void processCancel(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();

        try {
            SIPServerTransaction cancelTransaction = getCancelTransaction(requestEvent, request);
            SIPServerTransaction inviteTransaction = cancelTransaction == null ? null
                    : cancelTransaction.getCanceledInviteTransaction();

            if (cancelTransaction == null || inviteTransaction == null) {
                sendResponse(cancelTransaction, sipContext.messageFactory.createResponse(
                        Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST, request));
                return;
            }

            sendResponse(cancelTransaction, sipContext.messageFactory.createResponse(Response.OK, request));
            sendResponse(inviteTransaction, sipContext.messageFactory.createResponse(
                    Response.REQUEST_TERMINATED, inviteTransaction.getRequest()));
            cleanupSession(inviteTransaction.getDialog());
        } catch (ParseException | SipException | InvalidArgumentException e) {
            log.error("Error processing CANCEL request", e);
            throw new RuntimeException(e);
        }
    }

    private SIPServerTransaction getCancelTransaction(RequestEvent requestEvent, Request request) throws SipException {
        ServerTransaction serverTransaction = requestEvent.getServerTransaction();
        if (serverTransaction == null) {
            serverTransaction = sipContext.sipProvider.getNewServerTransaction(request);
        }
        if (serverTransaction instanceof SIPServerTransaction) {
            return (SIPServerTransaction) serverTransaction;
        }
        return null;
    }

    private void cleanupSession(Dialog dialog) {
        if (dialog == null) {
            return;
        }
        String dialogId = dialog.getDialogId();
        SipSession session = sipManage.getSipSession(dialogId);
        if (session == null) {
            return;
        }
        if (!session.isEstablished()) {
            session.setCancelled(true);
            sipManage.removeSipSession(dialogId);
        }
    }

    private static void sendResponse(ServerTransaction serverTransaction, Response response)
            throws SipException, InvalidArgumentException {
        if (serverTransaction == null) {
            throw new SipException("ServerTransaction is null");
        }
        serverTransaction.sendResponse(response);
    }
}

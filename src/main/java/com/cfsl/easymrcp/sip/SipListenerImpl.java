package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sip.handle.HandleAck;
import com.cfsl.easymrcp.sip.handle.HandleBye;
import com.cfsl.easymrcp.sip.handle.HandleInvite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sip.*;
import javax.sip.message.Request;

@Service
public class SipListenerImpl implements SipListener {
    @Autowired
    private SipContext sipContext;
    @Autowired
    private HandleInvite handleInvite;
    @Autowired
    private HandleAck handleAck;
    @Autowired
    private HandleBye handleBye;

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        if (request.getMethod().equals(Request.INVITE)) {
            handleInvite.handleInvite(requestEvent);
        } if (request.getMethod().equals(Request.ACK)) {
            handleAck.processAck(requestEvent);
        }   else if (request.getMethod().equals(Request.BYE)) {
            handleBye.processBye(requestEvent);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {

    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {

    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {

    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {

    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {

    }
}

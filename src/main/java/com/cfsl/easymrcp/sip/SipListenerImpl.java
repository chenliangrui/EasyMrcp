package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sip.handle.HandleAck;
import com.cfsl.easymrcp.sip.handle.HandleBye;
import com.cfsl.easymrcp.sip.handle.HandleInvite;
import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import gov.nist.javax.sip.header.From;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sip.*;
import javax.sip.header.CSeqHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

@Slf4j
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
    
    @Autowired
    private FSRegistrationClient fsRegistrationClient;

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
        Response response = responseEvent.getResponse();
        ClientTransaction clientTransaction = responseEvent.getClientTransaction();
        
        // 记录所有收到的响应
        CSeqHeader cseqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        if (cseqHeader != null) {
            log.info("收到响应: {} {} for {}", 
                    response.getStatusCode(),
                    response.getReasonPhrase(),
                    cseqHeader.getMethod());
        } else {
            log.info("收到响应: {} {}", 
                    response.getStatusCode(),
                    response.getReasonPhrase());
        }
        
        // 处理REGISTER响应
        if (clientTransaction != null) {
            Request request = clientTransaction.getRequest();
            if (request != null && request.getMethod().equals(Request.REGISTER)) {
                log.info("收到REGISTER响应: {} {}", response.getStatusCode(), response.getReasonPhrase());
                ClientTransaction tid = responseEvent.getClientTransaction();
                
                // 转发到FSRegistrationClient进行处理
                if (fsRegistrationClient != null) {
                    int status = response.getStatusCode();
                    if (status == Response.UNAUTHORIZED || status == Response.PROXY_AUTHENTICATION_REQUIRED) {
//                        fsRegistrationClient.handleAuthChallenge(response, clientTransaction);
                        String displayName = ((From) responseEvent.getClientTransaction().getRequest().getHeader("From")).getAddress().getDisplayName();
                        ClientTransaction inviteTid;
                        AuthenticationHelper authenticationHelper =
                                ((SipStackExt) sipContext.sipStack).getAuthenticationHelper(new AccountManagerImpl(displayName, displayName), sipContext.headerFactory);

                        try {
                            inviteTid = authenticationHelper.handleChallenge(response, tid, sipContext.sipProvider, 5);
                        } catch (SipException e) {
                            throw new RuntimeException(e);
                        }

                        try {
                            inviteTid.sendRequest();
                        } catch (SipException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        fsRegistrationClient.handleRegisterResponse(response);
                    }
                }
            }
        } else {
            // 处理没有关联事务的响应（可能是服务器端事务）
            log.warn("收到没有关联事务的响应: {} {}", 
                    response.getStatusCode(), 
                    response.getReasonPhrase());
        }
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

package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sip.handle.HandleAck;
import com.cfsl.easymrcp.sip.handle.HandleBye;
import com.cfsl.easymrcp.sip.handle.HandleInvite;
import com.cfsl.easymrcp.sip.handle.HandleOptions;
import com.cfsl.easymrcp.sip.handle.HandleRegister;

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
    private HandleOptions handleOptions;
    @Autowired
    private HandleRegister handleRegister;
    @Autowired
    private SipOptions sipOptions;

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        if (request.getMethod().equals(Request.INVITE)) {
            handleInvite.handleInvite(requestEvent);
        } else if (request.getMethod().equals(Request.ACK)) {
            handleAck.processAck(requestEvent);
        } else if (request.getMethod().equals(Request.BYE)) {
            handleBye.processBye(requestEvent);
        } else if (request.getMethod().equals(Request.OPTIONS)) {
            handleOptions.handleOptions(requestEvent);
        } else {
            log.warn("收到不支持的SIP请求方法: {}", request.getMethod());
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        ClientTransaction clientTransaction = responseEvent.getClientTransaction();
        
        // 记录所有收到的响应
        CSeqHeader cseqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        if (cseqHeader != null) {
            log.debug("收到响应: {} {} for {}",
                    response.getStatusCode(),
                    response.getReasonPhrase(),
                    cseqHeader.getMethod());
        } else {
            log.debug("收到响应: {} {}",
                    response.getStatusCode(),
                    response.getReasonPhrase());
        }
        
        // 处理各种响应
        if (clientTransaction != null) {
            Request request = clientTransaction.getRequest();
            if (request != null) {
                String method = request.getMethod();
                
                if (method.equals(Request.REGISTER)) {
                    // 处理REGISTER响应
                    if (handleRegister != null) {
                        handleRegister.handleRegisterResponse(responseEvent);
                    }
                } else if (method.equals(Request.OPTIONS)) {
                    // 处理OPTIONS响应
                    log.debug("收到OPTIONS响应: {} {}", response.getStatusCode(), response.getReasonPhrase());
                    if (sipOptions != null) {
                        sipOptions.handleOptionsResponse(response, clientTransaction);
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

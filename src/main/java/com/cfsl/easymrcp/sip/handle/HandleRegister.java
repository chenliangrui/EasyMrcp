package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sip.AccountManagerImpl;
import com.cfsl.easymrcp.sip.SipRegister;
import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import gov.nist.javax.sip.header.From;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sip.*;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * 处理SIP REGISTER响应
 */
@Slf4j
@Service
public class HandleRegister {
    
    @Autowired
    private SipContext sipContext;
    
    @Autowired
    private SipRegister sipRegister;
    
    /**
     * 处理REGISTER响应
     */
    public void handleRegisterResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        ClientTransaction clientTransaction = responseEvent.getClientTransaction();
        
        log.info("收到REGISTER响应: {} {}", response.getStatusCode(), response.getReasonPhrase());
        
        if (clientTransaction != null) {
            ClientTransaction tid = clientTransaction;
            
            // 转发到SipRegister进行处理
            if (sipRegister != null) {
                int status = response.getStatusCode();
                if (status == Response.UNAUTHORIZED || status == Response.PROXY_AUTHENTICATION_REQUIRED) {
                    handleAuthChallenge(response, clientTransaction);
                } else {
                    sipRegister.handleRegisterResponse(response);
                }
            }
        }
    }
    
    /**
     * 处理认证挑战
     */
    private void handleAuthChallenge(Response response, ClientTransaction clientTransaction) {
        try {
            String displayName = ((From) clientTransaction.getRequest().getHeader("From")).getAddress().getDisplayName();
            
            AuthenticationHelper authenticationHelper = ((SipStackExt) sipContext.sipStack)
                    .getAuthenticationHelper(new AccountManagerImpl(displayName, displayName), sipContext.headerFactory);

            ClientTransaction inviteTid = authenticationHelper.handleChallenge(response, clientTransaction, sipContext.sipProvider, 5);
            inviteTid.sendRequest();
            
            log.info("已发送带认证的REGISTER请求");
            
        } catch (SipException e) {
            log.error("处理认证挑战失败", e);
        }
    }
} 
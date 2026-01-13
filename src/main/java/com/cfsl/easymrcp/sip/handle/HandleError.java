package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sip.SipSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sip.message.Response;

/**
 * 处理SIP错误响应
 */
@Slf4j
@Service
public class HandleError {
    @Autowired
    SipContext sipContext;

    /**
     * 发送486 Busy Here响应
     */
    public void send486(SipSession session) {
        sendErrorResponse(session, 486, "Busy Here");
    }

    /**
     * 发送错误响应
     */
    public void sendErrorResponse(SipSession session, int statusCode, String reason) {
        try {
            if (session != null && session.getStx() != null) {
                Response response = sipContext.messageFactory.createResponse(statusCode, session.getRequestEvent().getRequest());
                response.addHeader(sipContext.getContactHeader());
                HandleOk.sendResponse(session.getStx(), response);
                log.info("已发送{} {}响应", statusCode, reason);
            }
        } catch (Exception e) {
            log.error("发送{}响应失败", statusCode, e);
        }
    }
}

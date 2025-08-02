package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sip.*;
import javax.sip.header.AcceptHeader;
import javax.sip.header.AllowHeader;
import javax.sip.header.SupportedHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 处理SIP OPTIONS请求
 * OPTIONS请求用于查询对端支持的SIP方法和能力
 */
@Slf4j
@Service
public class HandleOptions {
    
    @Autowired
    private SipContext sipContext;
    
    /**
     * 处理接收到的OPTIONS请求
     */
    public void handleOptions(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            
            log.info("收到OPTIONS请求: From={}, To={}", 
                    request.getHeader("From"), 
                    request.getHeader("To"));
            
            // 如果没有服务器事务，创建一个
            if (serverTransaction == null) {
                serverTransaction = sipContext.sipProvider.getNewServerTransaction(request);
            }
            
            // 创建200 OK响应
            Response response = sipContext.messageFactory.createResponse(Response.OK, request);
            
            // 添加Allow头部，声明支持的SIP方法
            List<String> allowedMethods = new ArrayList<>();
            allowedMethods.add(Request.INVITE);
            allowedMethods.add(Request.ACK);
            allowedMethods.add(Request.BYE);
            allowedMethods.add(Request.CANCEL);
            allowedMethods.add(Request.OPTIONS);
            allowedMethods.add(Request.REGISTER);
            
            AllowHeader allowHeader = sipContext.headerFactory.createAllowHeader(String.join(",", allowedMethods));
            response.addHeader(allowHeader);
            
            // 添加Accept头部，声明支持的内容类型
            AcceptHeader acceptHeader = sipContext.headerFactory.createAcceptHeader("application", "sdp");
            response.addHeader(acceptHeader);
            
            // 添加Supported头部，声明支持的SIP扩展
            SupportedHeader supportedHeader = sipContext.headerFactory.createSupportedHeader("timer,replaces");
            response.addHeader(supportedHeader);
            
            // 发送响应
            serverTransaction.sendResponse(response);
            
            log.info("已发送OPTIONS响应: 200 OK");
            
        } catch (ParseException | SipException | InvalidArgumentException e) {
            log.error("处理OPTIONS请求失败", e);
            try {
                // 发送500错误响应
                ServerTransaction serverTransaction = requestEvent.getServerTransaction();
                if (serverTransaction == null) {
                    serverTransaction = sipContext.sipProvider.getNewServerTransaction(requestEvent.getRequest());
                }
                Response errorResponse = sipContext.messageFactory.createResponse(
                        Response.SERVER_INTERNAL_ERROR, requestEvent.getRequest());
                serverTransaction.sendResponse(errorResponse);
            } catch (Exception ex) {
                log.error("发送错误响应失败", ex);
            }
        }
    }
} 
package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.util.Timeout;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.header.ExpiresHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SIP注册客户端
 * 使用现有的SIP基础设施，实现向FreeSWITCH的注册功能
 */
@Slf4j
@Service
public class SipRegister {

    @Autowired
    private SipContext sipContext;
    
    @Value("${fs.register.enabled:false}")
    private boolean registerEnabled;
    
    @Value("${fs.register.server:127.0.0.1}")
    private String fsServerIp;
    
    @Value("${fs.register.port:5060}")
    private int fsServerPort;
    
    @Value("${fs.register.username:easymrcp}")
    private String username;
    
    @Value("${fs.register.password:easymrcp}")
    private String password;
    
    @Value("${fs.register.domain:127.0.0.1}")
    private String domain;
    
    @Value("${fs.register.interval:60}")
    private int registerInterval;
    
    private Timeout reRegisterTimeout;
    private AtomicInteger cseq = new AtomicInteger(1);
    private String callId;
    private String fromTag;
    private String lastNonce;
    private String lastRealm;

    long invco = 1;
    
    @PostConstruct
    public void init() {
        if (!registerEnabled) {
            log.info("FreeSWITCH registration is disabled");
            return;
        }
        
        log.info("SIP注册客户端已初始化");
    }
    
    public void register() {
        try {
            // 使用已有的SIP组件创建和发送REGISTER请求
            log.info("发送注册请求到FreeSWITCH");
            Request registerRequest = createRegisterRequest("", username, fsServerIp, fsServerPort, registerInterval);
            
            // 设置标识，以便在SipListenerImpl中能够识别并处理响应
            ClientTransaction transaction = sipContext.sipProvider.getNewClientTransaction(registerRequest);
            // 保存当前callId用于后续更新注册
            CallIdHeader callIdHeader = (CallIdHeader) transaction.getRequest().getHeader(CallIdHeader.NAME);
            callId = callIdHeader.getCallId();
            
            transaction.sendRequest();
            log.info("REGISTER请求已发送到FreeSWITCH: {}:{}", fsServerIp, fsServerPort);
        } catch (Exception e) {
            log.error("注册FreeSWITCH失败", e);
        }
    }

    public Request createRegisterRequest(String callId, String name, String toSipAddress, int toSipPort, int expires) throws ParseException, InvalidArgumentException {
        String peerHostPort = toSipAddress + ":" + toSipPort;

        // create >From Header
        SipURI fromAddress = sipContext.addressFactory.createSipURI(name,
                toSipAddress);

        Address fromNameAddress = sipContext.addressFactory.createAddress(fromAddress);
        fromNameAddress.setDisplayName(name);
        FromHeader fromHeader = sipContext.headerFactory.createFromHeader(fromNameAddress,
                String.valueOf(new Random().nextInt(100000)));

        // create To Header
        SipURI toAddress = sipContext.addressFactory.createSipURI(name, toSipAddress);
        Address toNameAddress = sipContext.addressFactory.createAddress(toAddress);
        toNameAddress.setDisplayName(name);
        ToHeader toHeader = sipContext.headerFactory.createToHeader(toNameAddress, null);

        // create Request URI - 这里应该是FreeSWITCH服务器地址
        SipURI requestURI = sipContext.addressFactory.createSipURI(name, peerHostPort);

        // Create ViaHeaders - 这里使用本地地址和端口
        ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        ViaHeader viaHeader = sipContext.headerFactory.createViaHeader(
                sipContext.getSipServerIp(),  // 本地IP地址
                sipContext.getSipPort(),      // 本地SIP端口
                "udp",
                generateBranchId());
        // add via headers
        viaHeaders.add(viaHeader);

        // Create a new CallId header
        CallIdHeader callIdHeader;
        callIdHeader = sipContext.sipProvider.getNewCallId();
        if (callId.trim().length() > 0)
            callIdHeader.setCallId(callId);

        // Create a new Cseq header
        CSeqHeader cSeqHeader = sipContext.headerFactory.createCSeqHeader(invco,
                Request.REGISTER);
        invco++;

        // Create a new MaxForwardsHeader
        MaxForwardsHeader maxForwards = sipContext.headerFactory
                .createMaxForwardsHeader(70);

        // Create the request.
        Request request = sipContext.messageFactory.createRequest(requestURI,
                Request.REGISTER, callIdHeader, cSeqHeader, fromHeader, toHeader,
                viaHeaders, maxForwards);
                
        // Create contact headers - 这里使用本地地址和端口
        String host = sipContext.getSipServerIp();  // 本地IP地址

        // Create the contact name address.
        SipURI contactURI = sipContext.addressFactory.createSipURI(name, host);
        contactURI.setPort(sipContext.getSipPort());  // 本地SIP端口

        Address contactAddress = sipContext.addressFactory.createAddress(contactURI);

        // Add the contact address.
        contactAddress.setDisplayName(name);

        ContactHeader contactHeader = sipContext.headerFactory.createContactHeader(contactAddress);
        request.addHeader(contactHeader);

        // expires
        ExpiresHeader expiresHeader = sipContext.headerFactory.createExpiresHeader(expires);
        request.addHeader(expiresHeader);
        
        // 添加Route头部，确保请求发送到FreeSWITCH
        SipURI routeUri = sipContext.addressFactory.createSipURI(null, toSipAddress);
        routeUri.setPort(toSipPort);
        routeUri.setLrParam();
        Address routeAddress = sipContext.addressFactory.createAddress(routeUri);
        RouteHeader routeHeader = sipContext.headerFactory.createRouteHeader(routeAddress);
        request.addHeader(routeHeader);
        
        return request;
    }
    
    /**
     * 处理注册响应
     */
    public void handleRegisterResponse(Response response) {
        int status = response.getStatusCode();
        
        if (status == Response.OK) {
            log.info("FreeSWITCH注册成功！");
            
            // 获取过期时间
            ExpiresHeader expiresHeader = (ExpiresHeader) response.getHeader(ExpiresHeader.NAME);
            int expires = expiresHeader != null ? expiresHeader.getExpires() : registerInterval;
            
            // 注册刷新时间为过期时间的一半
            int refreshTime = expires / 2;
            log.info("注册有效期为{}秒，将在{}秒后自动更新", expires, refreshTime);
            
            // 使用时间轮设置定期注册更新
            if (reRegisterTimeout != null) {
                reRegisterTimeout.cancel();
            }
            
            reRegisterTimeout = SipUtils.wheelTimer.newTimeout(timeout -> {
                SipUtils.executeTask(() -> {
                    try {
                        log.info("执行注册更新");
                        register();
                    } catch (Exception e) {
                        log.error("注册更新失败", e);
                    }
                });
            }, refreshTime, TimeUnit.SECONDS);
            
        } else if (status == Response.UNAUTHORIZED || status == Response.PROXY_AUTHENTICATION_REQUIRED) {
            // 认证处理已在handleAuthChallenge中完成
            log.debug("收到认证请求，已在handleAuthChallenge中处理");
        } else {
            log.warn("注册失败，状态码: {}, 原因: {}", status, response.getReasonPhrase());
        }
    }
    
    private String generateBranchId() {
        // 生成标准的SIP branch ID，确保兼容各种SIP服务器
        return "z9hG4bK" + Math.abs(new Random().nextInt());
    }
    
    @PreDestroy
    public void shutdown() {
        if (reRegisterTimeout != null) {
            reRegisterTimeout.cancel();
        }
        log.info("SIP注册客户端已关闭");
    }
}
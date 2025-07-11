package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.common.SipServerStartedEvent;
import gov.nist.javax.sip.stack.SIPClientTransactionImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FreeSWITCH注册客户端
 * 使用现有的SIP基础设施，实现向FreeSWITCH的注册功能
 */
@Slf4j
@Service
public class FSRegistrationClient {

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
    
    private Timer timer;
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
        
        log.info("FreeSWITCH Registration Client initialized, waiting for SIP server to start");
    }
    
    /**
     * 监听SIP服务器启动事件
     * 当SIP服务器启动成功后，开始注册
     */
//    @EventListener
    public void onSipServerStarted(SipServerStartedEvent event) {
        if (!registerEnabled) {
            return;
        }
        
        try {
            log.info("SIP服务器已启动，开始FreeSWITCH注册");
            
            // 启动注册
            register();
            
            // 设置定期注册
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        register();
                    } catch (Exception e) {
                        log.error("Error during scheduled registration", e);
                    }
                }
            }, registerInterval * 1000, registerInterval * 1000);
            
        } catch (Exception e) {
            log.error("Failed to register with FreeSWITCH after SIP server started", e);
        }
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
     * 处理401认证响应
     */
    public void handleAuthChallenge(Response response, ClientTransaction transaction) {
        try {
            log.info("处理FreeSWITCH认证挑战");
            
            // 获取WWW-Authenticate头部
            WWWAuthenticateHeader wwwAuthenticateHeader = (WWWAuthenticateHeader) response.getHeader(WWWAuthenticateHeader.NAME);
            if (wwwAuthenticateHeader == null) {
                log.error("401响应中没有WWW-Authenticate头部");
                return;
            }
            
            log.info("收到认证挑战: realm={}, nonce={}", 
                     wwwAuthenticateHeader.getRealm(),
                     wwwAuthenticateHeader.getNonce());
            
            // 保存认证信息，用于后续请求
            lastNonce = wwwAuthenticateHeader.getNonce();
            lastRealm = wwwAuthenticateHeader.getRealm();
            
            // 获取原始请求
            Request originalRequest = transaction.getRequest();
            
            // 创建新请求
            Request registerRequest = (Request) originalRequest.clone();
            
            // 增加CSeq
            CSeqHeader cseqHeader = (CSeqHeader) registerRequest.getHeader(CSeqHeader.NAME);
            cseqHeader.setSeqNumber(invco++);
            
            // 生成Authorization头部
            AuthorizationHeader authorizationHeader = createAuthorizationHeader(
                    lastRealm,
                    lastNonce,
                    wwwAuthenticateHeader.getScheme(), 
                    registerRequest.getMethod(), 
                    registerRequest.getRequestURI().toString());
            
            registerRequest.addHeader(authorizationHeader);
            
            log.info("发送带认证信息的REGISTER请求");
            
            // 发送带有认证的请求
            ClientTransaction newClientTransaction = sipContext.sipProvider.getNewClientTransaction(registerRequest);
            newClientTransaction.sendRequest();
            log.info("已发送带认证的REGISTER请求到FreeSWITCH");
        } catch (Exception e) {
            log.error("处理认证失败", e);
        }
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
            
            log.info("注册有效期为{}秒，将在{}秒后自动更新", expires, expires > 10 ? expires - 10 : expires / 2);
            
            // 设置定期注册更新
            if (timer != null) {
                timer.cancel();
            }
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        log.info("执行注册更新");
                        register();
                    } catch (Exception e) {
                        log.error("注册更新失败", e);
                    }
                }
            }, (expires > 10 ? expires - 10 : expires / 2) * 1000);
            
        } else if (status == Response.UNAUTHORIZED || status == Response.PROXY_AUTHENTICATION_REQUIRED) {
            // 认证处理已在handleAuthChallenge中完成
            log.debug("收到认证请求，已在handleAuthChallenge中处理");
        } else {
            log.warn("注册失败，状态码: {}, 原因: {}", status, response.getReasonPhrase());
        }
    }
    
    private AuthorizationHeader createAuthorizationHeader(String realm, String nonce, String scheme, 
                                                         String method, String uri) 
            throws ParseException, InvalidArgumentException {
        // 创建Authorization头部
        AuthorizationHeader authHeader = sipContext.headerFactory.createAuthorizationHeader(scheme);
        authHeader.setUsername(username);
        authHeader.setRealm(realm);
        authHeader.setNonce(nonce);
        authHeader.setURI(sipContext.addressFactory.createURI(uri));
        
        // 计算digest响应
        String a1 = username + ":" + realm + ":" + password;
        String a2 = method + ":" + uri;
        
        // 计算MD5哈希
        String ha1 = getMD5(a1);
        String ha2 = getMD5(a2);
        String responseVal = getMD5(ha1 + ":" + nonce + ":" + ha2);
        
        authHeader.setResponse(responseVal);
        return authHeader;
    }
    
    private String getMD5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not found", e);
            return "";
        }
    }
    
    private String generateBranchId() {
        // 生成标准的SIP branch ID，确保兼容各种SIP服务器
        return "z9hG4bK" + Math.abs(new Random().nextInt());
    }
    
    @PreDestroy
    public void shutdown() {
        if (timer != null) {
            timer.cancel();
        }
        log.info("FreeSWITCH Registration Client shutdown");
    }
    
    /**
     * 包装ClientTransaction，用于处理REGISTER响应
     */
    public static class FSRegisterClientTransaction {
        private final ClientTransaction clientTransaction;
        private final FSRegistrationClient registrationClient;
        
        public FSRegisterClientTransaction(ClientTransaction clientTransaction, FSRegistrationClient registrationClient) {
            this.clientTransaction = clientTransaction;
            this.registrationClient = registrationClient;
            
            // 设置响应处理
            clientTransaction.setApplicationData(this);
        }
        
        public ClientTransaction getClientTransaction() {
            return clientTransaction;
        }
        
        public void handleResponse(ResponseEvent responseEvent) {
            Response response = responseEvent.getResponse();
            int status = response.getStatusCode();
            
            if (status == Response.UNAUTHORIZED || status == Response.PROXY_AUTHENTICATION_REQUIRED) {
                registrationClient.handleAuthChallenge(response, responseEvent.getClientTransaction());
            } else {
                registrationClient.handleRegisterResponse(response);
            }
        }
    }
}
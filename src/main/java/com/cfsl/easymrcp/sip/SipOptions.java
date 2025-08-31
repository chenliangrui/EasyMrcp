package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.util.Timeout;
import lombok.Getter;
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
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简化的SIP OPTIONS客户端
 * 每5秒向FS发送OPTIONS，连续3次失败视为离线，连续3次成功视为上线并重新注册
 */
@Slf4j
@Service
public class SipOptions {
    
    @Autowired
    private SipContext sipContext;
    
    @Autowired
    private SipRegister sipRegister;

    @Getter
    @Value("${fs.register.server:127.0.0.1}")
    private String fsServerIp;
    
    @Value("${fs.register.port:5060}")
    private int fsServerPort;
    
    private final AtomicLong cseqCounter = new AtomicLong(1);
    private final AtomicInteger consecutiveNoResponse = new AtomicInteger(0);  // 连续无响应次数
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);   // 连续成功次数
    private volatile boolean isOnline = true;  // 启动时假设在线
    private Timeout nextOptionsTimeout;
    
    @PostConstruct
    public void init() {
        log.info("SIP OPTIONS已初始化 - 目标: {}:{}", fsServerIp, fsServerPort);
    }
    
    /**
     * 启动OPTIONS定时器
     */
    public void start() {
        log.info("启动SIP OPTIONS定时器 - 目标: {}:{}", fsServerIp, fsServerPort);
        scheduleNextOptions();
    }
    
    /**
     * 调度下一次OPTIONS发送
     */
    private void scheduleNextOptions() {
        nextOptionsTimeout = SipUtils.wheelTimer.newTimeout(timeout -> {
            SipUtils.executeTask(() -> {
                sendOptions();
                scheduleNextOptions(); // 继续调度下一次
            });
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 发送OPTIONS请求
     */
    private void sendOptions() {
        try {
            Request optionsRequest = createOptionsRequest();
            ClientTransaction transaction = sipContext.sipProvider.getNewClientTransaction(optionsRequest);
            
            transaction.sendRequest();
            log.debug("发送OPTIONS {}:{}", fsServerIp, fsServerPort);
            
            // 连续无响应次数+1（发送了一次，还没收到响应）
            int noResponse = consecutiveNoResponse.incrementAndGet();
            if (noResponse >= 3) {
                log.warn("连续{}次OPTIONS无响应，标记为离线", noResponse);
                handleNoResponse();
            }
            
        } catch (Exception e) {
            log.error("发送OPTIONS失败", e);
            handleFailure();
        }
    }
    
    /**
     * 创建OPTIONS请求
     */
    private Request createOptionsRequest() throws ParseException, InvalidArgumentException {
        // 创建Request URI - 发送到FS服务器
        SipURI requestURI = sipContext.addressFactory.createSipURI(null, fsServerIp);
        requestURI.setPort(fsServerPort);
        
        // 创建From头部
        SipURI fromAddress = sipContext.addressFactory.createSipURI("easymrcp", sipContext.getSipServerIp());
        Address fromNameAddress = sipContext.addressFactory.createAddress(fromAddress);
        FromHeader fromHeader = sipContext.headerFactory.createFromHeader(fromNameAddress, SipUtils.getGUID());
        
        // 创建To头部
        SipURI toAddress = sipContext.addressFactory.createSipURI(null, fsServerIp);
        toAddress.setPort(fsServerPort);
        Address toNameAddress = sipContext.addressFactory.createAddress(toAddress);
        ToHeader toHeader = sipContext.headerFactory.createToHeader(toNameAddress, null);
        
        // 创建Via头部
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader viaHeader = sipContext.headerFactory.createViaHeader(
                sipContext.getSipServerIp(), sipContext.getSipPort(), "udp", generateBranchId());
        viaHeaders.add(viaHeader);
        
        // 创建Call-ID和CSeq头部
        CallIdHeader callIdHeader = sipContext.sipProvider.getNewCallId();
        CSeqHeader cSeqHeader = sipContext.headerFactory.createCSeqHeader(cseqCounter.getAndIncrement(), Request.OPTIONS);
        
        // 创建Max-Forwards头部
        MaxForwardsHeader maxForwards = sipContext.headerFactory.createMaxForwardsHeader(70);
        
        // 创建请求
        Request request = sipContext.messageFactory.createRequest(requestURI,
                Request.OPTIONS, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);
        
        // 添加Contact头部
        request.addHeader(sipContext.getContactHeader());
        
        // 添加Route头部，确保请求发送到外部FS服务器
        SipURI routeUri = sipContext.addressFactory.createSipURI(null, fsServerIp);
        routeUri.setPort(fsServerPort);
        routeUri.setLrParam();
        Address routeAddress = sipContext.addressFactory.createAddress(routeUri);
        RouteHeader routeHeader = sipContext.headerFactory.createRouteHeader(routeAddress);
        request.addHeader(routeHeader);
        
        return request;
    }
    
    /**
     * 处理OPTIONS响应
     */
    public void handleOptionsResponse(Response response, ClientTransaction transaction) {
        int statusCode = response.getStatusCode();
        log.debug("收到OPTIONS响应: {} {} from {}:{}",
                statusCode, response.getReasonPhrase(), fsServerIp, fsServerPort);
        
        if (statusCode >= 200 && statusCode < 300) {
            handleSuccess();
        } else {
            handleFailure();
        }
    }
    
        /**
     * 处理成功响应
     */
    private void handleSuccess() {
        // 重置无响应计数
        consecutiveNoResponse.set(0);
        
        int successes = consecutiveSuccesses.incrementAndGet();
        log.debug("OPTIONS成功响应，连续成功次数: {}", successes);
        
        if (!isOnline && successes >= 3) {
            // 从离线变为上线
            isOnline = true;
            log.info("FS服务器恢复上线，重新注册");
            
            // 重新注册（仅在FS恢复时）
            try {
                sipRegister.register();
            } catch (Exception e) {
                log.error("重新注册失败", e);
            }
        } else if (isOnline && successes == 1) {
            // 首次收到响应，确认在线状态
            log.debug("FS服务器在线确认");
        }
    }
    
    /**
     * 处理失败响应
     */
    private void handleFailure() {
        consecutiveSuccesses.set(0);
        consecutiveNoResponse.set(0); // 有响应就重置无响应计数
        log.debug("OPTIONS收到错误响应");
    }
    
    /**
     * 处理无响应情况
     */
    private void handleNoResponse() {
        consecutiveSuccesses.set(0);
        
        if (isOnline) {
            isOnline = false;
            log.warn("FS服务器连续无响应，标记为离线");
        }
    }
    
    /**
     * 生成Branch ID
     */
    private String generateBranchId() {
        return "z9hG4bK" + Math.abs(new Random().nextInt());
    }
    
    /**
     * 获取当前在线状态
     */
    public boolean isOnline() {
        return isOnline;
    }
    
    @PreDestroy
    public void shutdown() {
        if (nextOptionsTimeout != null) {
            nextOptionsTimeout.cancel();
        }
        log.info("SIP OPTIONS已关闭");
    }
} 
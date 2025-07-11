package com.cfsl.easymrcp.testutils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.TooManyListenersException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 独立测试FreeSWITCH注册的工具类
 * 使用方法：在application.yaml中添加 spring.profiles.active: fs-test 来激活这个测试组件
 */
@Slf4j
@Component
@Profile("fs-test")
public class FSRegistrationTester implements CommandLineRunner, SipListener {

    private SipStack sipStack;
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;
    private SipProvider sipProvider;
    
    private final AtomicInteger cseq = new AtomicInteger(1);
    private ClientTransaction regTrans;

    @Override
    public void run(String... args) throws Exception {
        log.info("启动FreeSWITCH注册测试...");
        
        // 配置参数
        String localIp = "192.168.1.100"; // 改为本机IP
        int localPort = 5080;
        String fsServerIp = "192.168.1.1"; // 改为FreeSWITCH服务器IP
        int fsServerPort = 5060;
        String username = "easymrcp";
        String password = "easymrcp";
        String domain = fsServerIp;
        
        try {
            // 初始化SIP栈
            initSipStack(localIp, localPort);
            
            // 发送注册请求
            sendRegister(fsServerIp, fsServerPort, username, domain);
            
            // 保持程序运行，等待响应
            log.info("注册请求已发送，等待响应...");
            Thread.sleep(10000);
            
        } catch (Exception e) {
            log.error("FreeSWITCH注册测试失败", e);
        }
    }
    
    private void initSipStack(String localIp, int localPort) throws PeerUnavailableException, TransportNotSupportedException, 
            InvalidArgumentException, ObjectInUseException, TooManyListenersException {
        
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "FSRegistrationTester");
        properties.setProperty("javax.sip.IP_ADDRESS", localIp);
        
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        sipStack = sipFactory.createSipStack(properties);
        
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();
        messageFactory = sipFactory.createMessageFactory();
        
        // 创建监听点和提供者
        ListeningPoint listeningPoint = sipStack.createListeningPoint(localIp, localPort, "udp");
        sipProvider = sipStack.createSipProvider(listeningPoint);
        sipProvider.addSipListener(this);
        
        log.info("SIP栈初始化完成，监听 {}:{}", localIp, localPort);
    }
    
    private void sendRegister(String fsServerIp, int fsServerPort, String username, String domain) 
            throws ParseException, InvalidArgumentException, SipException {
        
        // 创建To头部的SIP URI
        SipURI toUri = addressFactory.createSipURI(username, domain);
        Address toAddress = addressFactory.createAddress(toUri);
        toAddress.setDisplayName(username);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);
        
        // 创建From头部的SIP URI
        SipURI fromUri = addressFactory.createSipURI(username, domain);
        Address fromAddress = addressFactory.createAddress(fromUri);
        fromAddress.setDisplayName(username);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "reg" + System.currentTimeMillis());
        
        // 创建请求URI的SIP URI
        SipURI requestUri = addressFactory.createSipURI(null, fsServerIp);
        requestUri.setPort(fsServerPort);
        requestUri.setTransportParam("udp");
        
        // 创建Via头部
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        String localIp = sipStack.getIPAddress();
        int localPort = sipProvider.getListeningPoints()[0].getPort();
        ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort, "udp", null);
        viaHeader.setBranch("z9hG4bK" + System.currentTimeMillis());
        viaHeaders.add(viaHeader);
        
        // 创建Contact头部
        SipURI contactUri = addressFactory.createSipURI(username, localIp + ":" + localPort);
        Address contactAddress = addressFactory.createAddress(contactUri);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
        
        // 创建其他头部
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq.getAndIncrement(), Request.REGISTER);
        CallIdHeader callIdHeader = sipProvider.getNewCallId();
        
        // 创建请求
        Request request = messageFactory.createRequest(requestUri, Request.REGISTER, 
                callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);
        
        // 添加Contact和Expires头部
        request.addHeader(contactHeader);
        ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(3600);
        request.addHeader(expiresHeader);
        
        // 发送请求
        regTrans = sipProvider.getNewClientTransaction(request);
        regTrans.sendRequest();
        log.info("REGISTER请求已发送到FreeSWITCH {}:{}", fsServerIp, fsServerPort);
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        // 在这个简单测试中不处理请求
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        
        log.info("收到响应: {}", status);
        
        if (status == Response.OK) {
            log.info("注册成功！");
        } else if (status == Response.UNAUTHORIZED || status == Response.PROXY_AUTHENTICATION_REQUIRED) {
            log.info("需要认证，状态码: {}", status);
            // 在完整实现中，我们应该处理认证
        } else {
            log.info("注册失败，状态码: {}", status);
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.error("注册请求超时");
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("注册IO异常: {}", exceptionEvent.getTransport());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent event) {
        // 不重要
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent event) {
        // 不重要
    }
} 
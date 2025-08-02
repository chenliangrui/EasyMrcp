package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.common.SipContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.sip.*;
import java.util.Properties;
import java.util.TooManyListenersException;

@Slf4j
@Service
public class SipServer implements ApplicationRunner {
    SipContext sipContext;
    SipListenerImpl sipListener;
    private final ApplicationEventPublisher eventPublisher;


    public SipServer(SipContext sipContext, SipListenerImpl sipListener, ApplicationEventPublisher eventPublisher) {
        this.sipContext = sipContext;
        this.sipListener = sipListener;
        this.eventPublisher = eventPublisher;
        init();
    }

    public void init() {
        try {
            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "simpleSipServer");
            properties.setProperty("javax.sip.IP_ADDRESS", sipContext.getSipServerIp());
            properties.setProperty("javax.sip.OUTBOUND_PROXY", sipContext.getSipServerIp() + ":" + sipContext.getSipPort() + "/UDP");
//            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
//            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "simpleSipServerDebug.txt");
//            properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "simpleSipServerLog.txt");

            // Create SIP stack
            sipContext.sipFactory = SipFactory.getInstance();
            sipContext.sipFactory.setPathName("gov.nist");
            sipContext.sipStack = sipContext.sipFactory.createSipStack(properties);

            try {
                sipContext.headerFactory = sipContext.sipFactory.createHeaderFactory();
                sipContext.addressFactory = sipContext.sipFactory.createAddressFactory();
                sipContext.messageFactory = sipContext.sipFactory.createMessageFactory();
            } catch (SipException e) {
                log.warn(e.getMessage(), e);
            }

            // Create SIP provider
            ListeningPoint listeningPoint = sipContext.sipStack.createListeningPoint(sipContext.getSipServerIp(), sipContext.getSipPort(), "udp");
            sipContext.sipProvider = sipContext.sipStack.createSipProvider(listeningPoint);
            sipContext.sipProvider.addSipListener(sipListener);
        } catch (PeerUnavailableException | TransportNotSupportedException | InvalidArgumentException |
                 ObjectInUseException | TooManyListenersException e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    private SipOptions sipOptions;
    @Autowired
    private SipRegister sipRegister;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("SIP栈初始化完成");
        
        // 立即尝试注册
        log.info("开始FreeSWITCH注册");
        sipRegister.register();
        
        // 启动OPTIONS定时器进行连通性检测
        sipOptions.start();
        
        log.info("注册请求已发送，OPTIONS心跳已启动");
    }
}

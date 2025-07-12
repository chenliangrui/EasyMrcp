package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.common.SipServerStartedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.sip.*;
import java.util.Properties;
import java.util.TooManyListenersException;

@Slf4j
@Service
public class SipServer {
    SipContext sipContext;
    SipListenerImpl sipListener;
    private final ApplicationEventPublisher eventPublisher;
    @Autowired
    FSRegistrationClient fsRegistrationClient;

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
            properties.setProperty("javax.sip.OUTBOUND_PROXY", sipContext.getSipServerIp() + ":" + sipContext.getSipPort() +"/UDP");
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
            
            // 发布SIP服务器启动事件
            log.info("SIP服务器启动成功，发布SipServerStartedEvent事件");
            new Thread(() -> {
                try {
                    // 等待SIP栈完全启动
                    Thread.sleep(2000);
                    log.info("SIP栈初始化完成，开始FreeSWITCH注册");
                    fsRegistrationClient.register();
                } catch (InterruptedException e) {
                    log.error("FreeSWITCH注册延时过程中被中断", e);
                }
            }).start();
//            eventPublisher.publishEvent(new SipServerStartedEvent(this));
        } catch (PeerUnavailableException | TransportNotSupportedException | InvalidArgumentException |
                 ObjectInUseException | TooManyListenersException e) {
            throw new RuntimeException(e);
        }
    }
}

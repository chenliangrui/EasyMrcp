package com.cfsl.easymrcp.common;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

@Data
@Slf4j
@Component
public class SipContext {
    public SipFactory sipFactory;
    public SipStack sipStack;
    public SipProvider sipProvider;
    public AddressFactory addressFactory;
    public MessageFactory messageFactory;
    public HeaderFactory headerFactory;
    public List<String> supportProtocols = Arrays.asList("0", "8", "96");
    @Value("${sip.sipServer}")
    public String sipServerIp;
    @Value("${sip.sipPort}")
    public int sipPort;
    @Value("${sip.mrcpServerPort}")
    public int mrcpServerPort;
    public String displayName = "xiaohua";
    @Value("${rtp.asrStartPort}")
    public int asrStartPort;
    @Value("${rtp.asrStopPort}")
    public int asrStopPort;
    // 当前asrRtp端口
    @Value("${rtp.asrStartPort}")
    public int asrPort;
    @Value("${rtp.ttsStartPort}")
    public int ttsStartPort;
    @Value("${rtp.ttsStopPort}")
    public int ttsStopPort;
    // 当前asrRtp端口
    @Value("${rtp.ttsStartPort}")
    public int ttsPort;

    public int getAsrRtpPort() {
        if (asrPort > asrStopPort) {
            asrPort = asrStartPort;
        }
        return asrPort++;
    }

    public int getTtsRtpPort() {
        if (ttsPort > ttsStopPort) {
            ttsPort = ttsStartPort;
        }
        return ttsPort++;
    }

    public ContactHeader getContactHeader() {
        return getContactHeader(displayName, sipServerIp);
    }

    public ContactHeader getContactHeader(String displayName, String contactIp){
        HeaderFactory headerFactory = null;
        try {
            headerFactory = sipFactory.createHeaderFactory();
            AddressFactory addressFactory = sipFactory.createAddressFactory();
            SipURI contactURI = addressFactory.createSipURI(displayName, contactIp);
            Address contactAddress = addressFactory.createAddress(contactURI);
            contactAddress.setDisplayName(displayName);
            ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
            contactURI.setPort(sipProvider.getListeningPoint("udp").getPort());
            return contactHeader;
        } catch (PeerUnavailableException | ParseException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}

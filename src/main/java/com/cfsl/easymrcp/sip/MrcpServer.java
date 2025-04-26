package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.common.SipContext;
import lombok.Data;
import org.mrcp4j.server.MrcpServerSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Data
@Service
public class MrcpServer {
    @Autowired
    SipContext sipContext;
    MrcpServerSocket mrcpServerSocket;

    public MrcpServer(SipContext sipContext) throws IOException {
        this.sipContext = sipContext;
        mrcpServerSocket = new MrcpServerSocket(sipContext.getSipServerIp(), sipContext.getMrcpServerPort());
    }

}

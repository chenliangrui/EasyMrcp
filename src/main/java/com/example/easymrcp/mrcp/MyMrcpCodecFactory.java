package com.example.easymrcp.mrcp;

import org.apache.mina.protocol.ProtocolCodecFactory;
import org.apache.mina.protocol.ProtocolDecoder;
import org.apache.mina.protocol.ProtocolEncoder;
import org.mrcp4j.server.MrcpRequestDecoder;

public class MyMrcpCodecFactory implements ProtocolCodecFactory {

    public ProtocolDecoder newDecoder() {
        return new MrcpRequestDecoder();
    }

    public ProtocolEncoder newEncoder() {
        return new MrcpMessageEncoder();
    }

}

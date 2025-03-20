package com.example.easymrcp.mrcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeoutException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.mina.common.TransportType;
import org.apache.mina.io.IoAcceptor;
import org.apache.mina.protocol.ProtocolCodecFactory;
import org.apache.mina.registry.Service;
import org.apache.mina.registry.ServiceRegistry;
import org.apache.mina.registry.SimpleServiceRegistry;
import org.mrcp4j.MrcpEventName;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.MrcpResourceType;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.MrcpResponse;
import org.mrcp4j.message.request.MrcpRequest;
import org.mrcp4j.server.*;
import org.mrcp4j.server.delegator.RecogOnlyRequestDelegator;
import org.mrcp4j.server.delegator.RecorderRequestDelegator;
import org.mrcp4j.server.delegator.SpeakVerifyRequestDelegator;
import org.mrcp4j.server.delegator.SpeechSynthRequestDelegator;
import org.mrcp4j.server.delegator.VoiceEnrollmentRequestDelegator;
import org.mrcp4j.server.mina.IoTextLoggingFilter;
import org.mrcp4j.server.mina.SimpleProtocolProvider;
import org.mrcp4j.server.provider.RecogOnlyRequestHandler;
import org.mrcp4j.server.provider.RecorderRequestHandler;
import org.mrcp4j.server.provider.SpeakVerifyRequestHandler;
import org.mrcp4j.server.provider.SpeechSynthRequestHandler;
import org.mrcp4j.server.provider.VoiceEnrollmentRequestHandler;

public class MrcpServerSocket {
    private static Logger _log = LogManager.getLogger(org.mrcp4j.server.MrcpServerSocket.class);
    private static ProtocolCodecFactory CODEC_FACTORY = new MrcpCodecFactory();
    private MrcpRequestProcessorImpl _requestProcessorImpl;
    private int _port;

    public MrcpServerSocket(int port) throws IOException {
        this._port = port;
        this._requestProcessorImpl = new MrcpRequestProcessorImpl();
        ServiceRegistry registry = new SimpleServiceRegistry();
        addLogger(registry);
        Service service = new Service("MRCPv2", TransportType.SOCKET, new InetSocketAddress("172.16.2.29", port));
        registry.bind(service, new SimpleProtocolProvider(CODEC_FACTORY, new MrcpProtocolHandler(this._requestProcessorImpl)));
        if (_log.isDebugEnabled()) {
            _log.debug("MRCPv2 protocol provider listening on port " + port);
        }

    }

    public int getPort() {
        return this._port;
    }

    public void openChannel(String channelID, RecogOnlyRequestHandler requestHandler) {
        validateChannelID(channelID, RecogOnlyRequestHandler.RESOURCE_TYPES);
        this.openChannel(channelID, (MrcpRequestHandler)(new RecogOnlyRequestDelegator(requestHandler)));
    }

    public void openChannel(String channelID, VoiceEnrollmentRequestHandler requestHandler) {
        validateChannelID(channelID, VoiceEnrollmentRequestHandler.RESOURCE_TYPES);
        this.openChannel(channelID, (MrcpRequestHandler)(new VoiceEnrollmentRequestDelegator(requestHandler)));
    }

    public void openChannel(String channelID, SpeechSynthRequestHandler requestHandler) {
        validateChannelID(channelID, SpeechSynthRequestHandler.RESOURCE_TYPES);
        this.openChannel(channelID, (MrcpRequestHandler)(new SpeechSynthRequestDelegator(requestHandler)));
    }

    public void openChannel(String channelID, SpeakVerifyRequestHandler requestHandler) {
        validateChannelID(channelID, SpeakVerifyRequestHandler.RESOURCE_TYPES);
        this.openChannel(channelID, (MrcpRequestHandler)(new SpeakVerifyRequestDelegator(requestHandler)));
    }

    public void openChannel(String channelID, RecorderRequestHandler requestHandler) {
        validateChannelID(channelID, RecorderRequestHandler.RESOURCE_TYPES);
        this.openChannel(channelID, (MrcpRequestHandler)(new RecorderRequestDelegator(requestHandler)));
    }

    private static void validateChannelID(String channelID, MrcpResourceType[] expected) {
        MrcpResourceType actual = MrcpResourceType.fromChannelID(channelID);
        MrcpResourceType[] var3 = expected;
        int var4 = expected.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            MrcpResourceType type = var3[var5];
            if (type.equals(actual)) {
                return;
            }
        }

        throw new IllegalArgumentException("Incorrect channel resource type for specified request handler: " + channelID);
    }

    private void openChannel(String channelID, MrcpRequestHandler requestHandler) {
        this._requestProcessorImpl.registerRequestHandler(channelID, requestHandler);
    }

    public void closeChannel(String channelID) {
        this._requestProcessorImpl.unregisterRequestHandler(channelID);
    }

    private static void addLogger(ServiceRegistry registry) {
        IoAcceptor acceptor = registry.getIoAcceptor(TransportType.SOCKET);
        acceptor.getFilterChain().addLast("logger", new IoTextLoggingFilter());
        _log.debug("Logging ON");
    }
}

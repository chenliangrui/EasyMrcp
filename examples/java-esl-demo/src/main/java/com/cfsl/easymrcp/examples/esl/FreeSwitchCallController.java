package com.cfsl.easymrcp.examples.esl;

import com.cfsl.easymrcp.examples.esl.config.EasyMrcpDemoProperties;
import link.thingscloud.freeswitch.esl.InboundClient;
import link.thingscloud.freeswitch.esl.transport.message.EslHeaders;
import link.thingscloud.freeswitch.esl.transport.message.EslMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Component
public class FreeSwitchCallController {

    private final InboundClient inboundClient;
    private final String sipUser;
    private final Supplier<String> bridgeLegUuidSupplier;

    @Autowired
    public FreeSwitchCallController(InboundClient inboundClient, EasyMrcpDemoProperties properties) {
        this(inboundClient, properties, () -> UUID.randomUUID().toString());
    }

    FreeSwitchCallController(InboundClient inboundClient,
                             EasyMrcpDemoProperties properties,
                             Supplier<String> bridgeLegUuidSupplier) {
        this.inboundClient = inboundClient;
        String sipUser = properties.getSipUser();
        if (sipUser == null || sipUser.trim().isEmpty()) {
            throw new IllegalArgumentException("easy-mrcp.sip-user must be configured");
        }
        this.sipUser = sipUser;
        this.bridgeLegUuidSupplier = bridgeLegUuidSupplier;
    }

    public void answerAndBridge(String address, String aLegUuid, Consumer<String> onFailure) {
        String bLegUuid = bridgeLegUuidSupplier.get();
        // 先接听 A-leg，再创建带标记的 B-leg，最后按两个 UUID 桥接。
        if (!runCommand(address, aLegUuid, "uuid_answer", aLegUuid, onFailure)) {
            return;
        }
        if (!runCommand(address, aLegUuid, "originate", originateArguments(aLegUuid, bLegUuid), onFailure)) {
            return;
        }
        bridge(address, aLegUuid, bLegUuid, onFailure);
    }

    private void bridge(String address, String aLegUuid, String bLegUuid, Consumer<String> onFailure) {
        try {
            EslMessage bridgeReply = inboundClient.sendSyncApiCommand(
                    address,
                    "uuid_bridge",
                    aLegUuid + " " + bLegUuid
            );
            String bridgeReplyText = replyText(bridgeReply);
            if (isSuccessful(bridgeReplyText)) {
                return;
            }

            cleanupBleg(address, bLegUuid);
            reject(aLegUuid, "uuid_bridge", bridgeReplyText, onFailure);
        } catch (RuntimeException dispatchException) {
            cleanupBleg(address, bLegUuid);
            reject(aLegUuid, "uuid_bridge", dispatchException.getMessage(), onFailure);
        }
    }

    private void cleanupBleg(String address, String bLegUuid) {
        try {
            inboundClient.sendSyncApiCommand(
                    address,
                    "uuid_kill",
                    bLegUuid + " NORMAL_CLEARING"
            );
        } catch (RuntimeException cleanupException) {
            log.warn("Failed to clean up FreeSWITCH B-leg, uuid={}", bLegUuid, cleanupException);
        }
    }

    private boolean runCommand(String address,
                               String aLegUuid,
                               String command,
                               String arguments,
                               Consumer<String> onFailure) {
        try {
            EslMessage reply = inboundClient.sendSyncApiCommand(address, command, arguments);
            String replyText = replyText(reply);
            if (isSuccessful(replyText)) {
                return true;
            }
            reject(aLegUuid, command, replyText, onFailure);
        } catch (RuntimeException dispatchException) {
            reject(aLegUuid, command, dispatchException.getMessage(), onFailure);
        }
        return false;
    }

    private String originateArguments(String aLegUuid, String bLegUuid) {
        // 标记 B-leg，避免其生命周期事件被作为新呼叫再次建会话。
        return "{origination_uuid=" + bLegUuid
                + ",easymrcp_bridge_leg=true,sip_h_X-EasyMRCP=" + aLegUuid
                + "}user/" + sipUser + " &park()";
    }

    private boolean isSuccessful(String replyText) {
        return replyText != null && replyText.startsWith("+OK");
    }

    private String replyText(EslMessage reply) {
        if (reply == null) {
            return null;
        }

        List<String> bodyLines = reply.getBodyLines();
        if (bodyLines != null) {
            for (String bodyLine : bodyLines) {
                if (bodyLine != null && !bodyLine.trim().isEmpty()) {
                    return bodyLine.trim();
                }
            }
        }
        return reply.getHeaderValue(EslHeaders.Name.REPLY_TEXT);
    }

    private void reject(String uuid, String command, String replyText, Consumer<String> onFailure) {
        log.warn("FreeSWITCH command rejected, uuid={}, command={}, reply={}", uuid, command, replyText);
        onFailure.accept(command);
    }
}

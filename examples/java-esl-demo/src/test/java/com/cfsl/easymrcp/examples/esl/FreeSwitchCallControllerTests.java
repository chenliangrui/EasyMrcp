package com.cfsl.easymrcp.examples.esl;

import com.cfsl.easymrcp.examples.esl.config.EasyMrcpDemoProperties;
import link.thingscloud.freeswitch.esl.InboundClient;
import link.thingscloud.freeswitch.esl.transport.message.EslHeaders;
import link.thingscloud.freeswitch.esl.transport.message.EslMessage;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreeSwitchCallControllerTests {

    private static final String ADDRESS = "127.0.0.1:8021";
    private static final String A_LEG_UUID = "a-leg-uuid";
    private static final String B_LEG_UUID = "b-leg-uuid";
    private static final String DEFAULT_SIP_USER = "1020";
    private static final String CONFIGURED_SIP_USER = "2099";
    private static final String ORIGINATE_ARGUMENTS =
            "{origination_uuid=" + B_LEG_UUID
                    + ",easymrcp_bridge_leg=true,sip_h_X-EasyMRCP=" + A_LEG_UUID
                    + "}user/" + DEFAULT_SIP_USER + " &park()";

    @Test
    void canBeCreatedBySpringWhenInboundClientIsAvailable() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(InboundClient.class, () -> mock(InboundClient.class));
            context.registerBean(EasyMrcpDemoProperties.class, () -> properties(DEFAULT_SIP_USER));
            context.register(FreeSwitchCallController.class);
            context.refresh();

            assertNotNull(context.getBean(FreeSwitchCallController.class));
        }
    }

    @Test
    void rejectsMissingSipUserAtConstruction() {
        assertInvalidSipUser(properties(null));
    }

    @Test
    void rejectsBlankSipUserAtConstruction() {
        assertInvalidSipUser(properties("   "));
    }

    @Test
    void answersOriginatesAndBridgesSynchronouslyWhenAllRepliesAreSuccessful() {
        InboundClient inboundClient = mock(InboundClient.class);
        Consumer<String> onFailure = failureConsumer();
        replyTo(inboundClient, "uuid_answer", A_LEG_UUID, apiReplyWith("+OK answered"));
        replyTo(inboundClient, "originate", ORIGINATE_ARGUMENTS, replyWith("+OK originated"));
        replyTo(inboundClient, "uuid_bridge", A_LEG_UUID + " " + B_LEG_UUID, apiReplyWith("+OK bridged"));

        controller(inboundClient).answerAndBridge(ADDRESS, A_LEG_UUID, onFailure);

        InOrder commandOrder = inOrder(inboundClient);
        commandOrder.verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("uuid_answer"), eq(A_LEG_UUID));
        commandOrder.verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("originate"), eq(ORIGINATE_ARGUMENTS));
        commandOrder.verify(inboundClient).sendSyncApiCommand(
                eq(ADDRESS), eq("uuid_bridge"), eq(A_LEG_UUID + " " + B_LEG_UUID));
        verify(onFailure, never()).accept(any());
    }

    @Test
    void originatesToConfiguredSipUser() {
        InboundClient inboundClient = mock(InboundClient.class);
        Consumer<String> onFailure = failureConsumer();
        EasyMrcpDemoProperties properties = new EasyMrcpDemoProperties();
        properties.setSipUser(CONFIGURED_SIP_USER);
        String configuredOriginateArguments = "{origination_uuid=" + B_LEG_UUID
                + ",easymrcp_bridge_leg=true,sip_h_X-EasyMRCP=" + A_LEG_UUID
                + "}user/" + CONFIGURED_SIP_USER + " &park()";
        replyTo(inboundClient, "uuid_answer", A_LEG_UUID, apiReplyWith("+OK answered"));
        replyTo(inboundClient, "originate", configuredOriginateArguments, apiReplyWith("+OK originated"));
        replyTo(inboundClient, "uuid_bridge", A_LEG_UUID + " " + B_LEG_UUID, apiReplyWith("+OK bridged"));

        controller(inboundClient, properties).answerAndBridge(ADDRESS, A_LEG_UUID, onFailure);

        verify(inboundClient).sendSyncApiCommand(
                eq(ADDRESS), eq("originate"), eq(configuredOriginateArguments));
        verify(onFailure, never()).accept(any());
    }

    @Test
    void reportsAnswerFailureAndDoesNotOriginateWhenAnswerReplyIsRejected() {
        InboundClient inboundClient = mock(InboundClient.class);
        Consumer<String> onFailure = failureConsumer();
        replyTo(inboundClient, "uuid_answer", A_LEG_UUID, replyWith(null));

        controller(inboundClient).answerAndBridge(ADDRESS, A_LEG_UUID, onFailure);

        verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("uuid_answer"), eq(A_LEG_UUID));
        verify(inboundClient, never()).sendSyncApiCommand(eq(ADDRESS), eq("originate"), eq(ORIGINATE_ARGUMENTS));
        verify(inboundClient, never()).sendSyncApiCommand(
                eq(ADDRESS), eq("uuid_bridge"), eq(A_LEG_UUID + " " + B_LEG_UUID));
        verify(onFailure).accept("uuid_answer");
    }

    @Test
    void reportsAnswerDispatchFailureAndDoesNotOriginate() {
        InboundClient inboundClient = mock(InboundClient.class);
        Consumer<String> onFailure = failureConsumer();
        throwOnDispatch(inboundClient, "uuid_answer", A_LEG_UUID);

        controller(inboundClient).answerAndBridge(ADDRESS, A_LEG_UUID, onFailure);

        verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("uuid_answer"), eq(A_LEG_UUID));
        verify(inboundClient, never()).sendSyncApiCommand(eq(ADDRESS), eq("originate"), eq(ORIGINATE_ARGUMENTS));
        verify(onFailure).accept("uuid_answer");
    }

    @Test
    void reportsOriginateFailureAndDoesNotBridgeWhenBlegCannotBeCreated() {
        InboundClient inboundClient = mock(InboundClient.class);
        Consumer<String> onFailure = failureConsumer();
        replyTo(inboundClient, "uuid_answer", A_LEG_UUID, apiReplyWith("+OK answered"));
        replyTo(inboundClient, "originate", ORIGINATE_ARGUMENTS, apiReplyWith("-ERR user/1020 unavailable"));

        controller(inboundClient).answerAndBridge(ADDRESS, A_LEG_UUID, onFailure);

        verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("originate"), eq(ORIGINATE_ARGUMENTS));
        verify(inboundClient, never()).sendSyncApiCommand(
                eq(ADDRESS), eq("uuid_bridge"), eq(A_LEG_UUID + " " + B_LEG_UUID));
        verify(onFailure).accept("originate");
    }

    @Test
    void reportsOriginateDispatchFailureAndDoesNotBridge() {
        InboundClient inboundClient = mock(InboundClient.class);
        Consumer<String> onFailure = failureConsumer();
        replyTo(inboundClient, "uuid_answer", A_LEG_UUID, apiReplyWith("+OK answered"));
        throwOnDispatch(inboundClient, "originate", ORIGINATE_ARGUMENTS);

        controller(inboundClient).answerAndBridge(ADDRESS, A_LEG_UUID, onFailure);

        verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("originate"), eq(ORIGINATE_ARGUMENTS));
        verify(inboundClient, never()).sendSyncApiCommand(
                eq(ADDRESS), eq("uuid_bridge"), eq(A_LEG_UUID + " " + B_LEG_UUID));
        verify(onFailure).accept("originate");
    }

    @Test
    void killsBlegAndReportsBridgeFailure() {
        InboundClient inboundClient = mock(InboundClient.class);
        Consumer<String> onFailure = failureConsumer();
        replyTo(inboundClient, "uuid_answer", A_LEG_UUID, apiReplyWith("+OK answered"));
        replyTo(inboundClient, "originate", ORIGINATE_ARGUMENTS, apiReplyWith("+OK originated"));
        replyTo(inboundClient, "uuid_bridge", A_LEG_UUID + " " + B_LEG_UUID, apiReplyWith("-ERR bridge failed"));
        replyTo(inboundClient, "uuid_kill", B_LEG_UUID + " NORMAL_CLEARING", apiReplyWith("+OK killed"));

        controller(inboundClient).answerAndBridge(ADDRESS, A_LEG_UUID, onFailure);

        InOrder commandOrder = inOrder(inboundClient);
        commandOrder.verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("uuid_answer"), eq(A_LEG_UUID));
        commandOrder.verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("originate"), eq(ORIGINATE_ARGUMENTS));
        commandOrder.verify(inboundClient).sendSyncApiCommand(
                eq(ADDRESS), eq("uuid_bridge"), eq(A_LEG_UUID + " " + B_LEG_UUID));
        commandOrder.verify(inboundClient).sendSyncApiCommand(
                eq(ADDRESS), eq("uuid_kill"), eq(B_LEG_UUID + " NORMAL_CLEARING"));
        verify(onFailure).accept("uuid_bridge");
    }

    @Test
    void killsBlegAndReportsBridgeDispatchFailure() {
        InboundClient inboundClient = mock(InboundClient.class);
        Consumer<String> onFailure = failureConsumer();
        replyTo(inboundClient, "uuid_answer", A_LEG_UUID, apiReplyWith("+OK answered"));
        replyTo(inboundClient, "originate", ORIGINATE_ARGUMENTS, apiReplyWith("+OK originated"));
        throwOnDispatch(inboundClient, "uuid_bridge", A_LEG_UUID + " " + B_LEG_UUID);

        controller(inboundClient).answerAndBridge(ADDRESS, A_LEG_UUID, onFailure);

        InOrder commandOrder = inOrder(inboundClient, onFailure);
        commandOrder.verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("uuid_answer"), eq(A_LEG_UUID));
        commandOrder.verify(inboundClient).sendSyncApiCommand(eq(ADDRESS), eq("originate"), eq(ORIGINATE_ARGUMENTS));
        commandOrder.verify(inboundClient).sendSyncApiCommand(
                eq(ADDRESS), eq("uuid_bridge"), eq(A_LEG_UUID + " " + B_LEG_UUID));
        commandOrder.verify(inboundClient).sendSyncApiCommand(
                eq(ADDRESS), eq("uuid_kill"), eq(B_LEG_UUID + " NORMAL_CLEARING"));
        commandOrder.verify(onFailure).accept("uuid_bridge");
    }

    @Test
    void reportsBridgeFailureWhenBlegCleanupDispatchFails() {
        InboundClient inboundClient = mock(InboundClient.class);
        Consumer<String> onFailure = failureConsumer();
        replyTo(inboundClient, "uuid_answer", A_LEG_UUID, apiReplyWith("+OK answered"));
        replyTo(inboundClient, "originate", ORIGINATE_ARGUMENTS, apiReplyWith("+OK originated"));
        throwOnDispatch(inboundClient, "uuid_bridge", A_LEG_UUID + " " + B_LEG_UUID);
        throwOnDispatch(inboundClient, "uuid_kill", B_LEG_UUID + " NORMAL_CLEARING");

        controller(inboundClient).answerAndBridge(ADDRESS, A_LEG_UUID, onFailure);

        verify(inboundClient).sendSyncApiCommand(
                eq(ADDRESS), eq("uuid_kill"), eq(B_LEG_UUID + " NORMAL_CLEARING"));
        verify(onFailure).accept("uuid_bridge");
    }

    private static FreeSwitchCallController controller(InboundClient inboundClient) {
        return controller(inboundClient, properties(DEFAULT_SIP_USER));
    }

    private static FreeSwitchCallController controller(InboundClient inboundClient,
                                                       EasyMrcpDemoProperties properties) {
        return new FreeSwitchCallController(inboundClient, properties, () -> B_LEG_UUID);
    }

    private static void assertInvalidSipUser(EasyMrcpDemoProperties properties) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new FreeSwitchCallController(mock(InboundClient.class), properties));

        assertTrue(exception.getMessage().contains("easy-mrcp.sip-user"));
    }

    private static EasyMrcpDemoProperties properties(String sipUser) {
        EasyMrcpDemoProperties properties = new EasyMrcpDemoProperties();
        properties.setSipUser(sipUser);
        return properties;
    }

    private static void replyTo(InboundClient inboundClient, String command, String arguments, EslMessage reply) {
        when(inboundClient.sendSyncApiCommand(ADDRESS, command, arguments)).thenReturn(reply);
    }

    private static void throwOnDispatch(InboundClient inboundClient, String command, String arguments) {
        doThrow(new IllegalStateException("ESL dispatch failed"))
                .when(inboundClient)
                .sendSyncApiCommand(eq(ADDRESS), eq(command), eq(arguments));
    }

    private static EslMessage replyWith(String replyText) {
        EslMessage reply = mock(EslMessage.class);
        when(reply.getHeaderValue(EslHeaders.Name.REPLY_TEXT)).thenReturn(replyText);
        return reply;
    }

    private static EslMessage apiReplyWith(String responseBody) {
        EslMessage reply = mock(EslMessage.class);
        when(reply.getBodyLines()).thenReturn(List.of(responseBody));
        return reply;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<String> failureConsumer() {
        return mock(Consumer.class);
    }
}

package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sip.SipManage;
import com.cfsl.easymrcp.sip.SipSession;
import gov.nist.javax.sip.stack.SIPServerTransaction;
import org.junit.jupiter.api.Test;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandleCancelTests {

    @Test
    void processCancel_shouldSend200And487AndRemoveUnestablishedSession() throws Exception {
        HandleCancel handleCancel = new HandleCancel();
        SipManage sipManage = new SipManage();
        SipContext sipContext = mock(SipContext.class);
        MessageFactory messageFactory = mock(MessageFactory.class);
        sipContext.messageFactory = messageFactory;
        setField(handleCancel, "sipManage", sipManage);
        setField(handleCancel, "sipContext", sipContext);

        Dialog dialog = mock(Dialog.class);
        when(dialog.getDialogId()).thenReturn("cancel-invite-dialog");

        SipSession session = new SipSession();
        session.setDialog(dialog);
        session.setEstablished(false);
        sipManage.addSipSession(session);

        Request cancelRequest = mock(Request.class);
        Request originalInvite = mock(Request.class);
        RequestEvent cancelEvent = mock(RequestEvent.class);
        SIPServerTransaction cancelTransaction = mock(SIPServerTransaction.class);
        SIPServerTransaction inviteTransaction = mock(SIPServerTransaction.class);
        Response cancelOk = mock(Response.class);
        Response requestTerminated = mock(Response.class);

        when(cancelEvent.getRequest()).thenReturn(cancelRequest);
        when(cancelEvent.getServerTransaction()).thenReturn(cancelTransaction);
        when(cancelTransaction.getCanceledInviteTransaction()).thenReturn(inviteTransaction);
        when(inviteTransaction.getRequest()).thenReturn(originalInvite);
        when(inviteTransaction.getDialog()).thenReturn(dialog);
        when(messageFactory.createResponse(Response.OK, cancelRequest)).thenReturn(cancelOk);
        when(messageFactory.createResponse(Response.REQUEST_TERMINATED, originalInvite)).thenReturn(requestTerminated);

        handleCancel.processCancel(cancelEvent);

        verify(cancelTransaction).sendResponse(cancelOk);
        verify(inviteTransaction).sendResponse(requestTerminated);
        assertNull(sipManage.getSipSession("cancel-invite-dialog"));
    }

    @Test
    void processCancel_shouldSend200And487AndKeepEstablishedSession() throws Exception {
        HandleCancel handleCancel = new HandleCancel();
        SipManage sipManage = new SipManage();
        SipContext sipContext = mock(SipContext.class);
        MessageFactory messageFactory = mock(MessageFactory.class);
        sipContext.messageFactory = messageFactory;
        setField(handleCancel, "sipManage", sipManage);
        setField(handleCancel, "sipContext", sipContext);

        Dialog dialog = mock(Dialog.class);
        when(dialog.getDialogId()).thenReturn("cancel-reinvite-dialog");

        SipSession session = new SipSession();
        session.setDialog(dialog);
        session.setEstablished(true);
        sipManage.addSipSession(session);

        Request cancelRequest = mock(Request.class);
        Request originalInvite = mock(Request.class);
        RequestEvent cancelEvent = mock(RequestEvent.class);
        SIPServerTransaction cancelTransaction = mock(SIPServerTransaction.class);
        SIPServerTransaction inviteTransaction = mock(SIPServerTransaction.class);
        Response cancelOk = mock(Response.class);
        Response requestTerminated = mock(Response.class);

        when(cancelEvent.getRequest()).thenReturn(cancelRequest);
        when(cancelEvent.getServerTransaction()).thenReturn(cancelTransaction);
        when(cancelTransaction.getCanceledInviteTransaction()).thenReturn(inviteTransaction);
        when(inviteTransaction.getRequest()).thenReturn(originalInvite);
        when(inviteTransaction.getDialog()).thenReturn(dialog);
        when(messageFactory.createResponse(Response.OK, cancelRequest)).thenReturn(cancelOk);
        when(messageFactory.createResponse(Response.REQUEST_TERMINATED, originalInvite)).thenReturn(requestTerminated);

        handleCancel.processCancel(cancelEvent);

        verify(cancelTransaction).sendResponse(cancelOk);
        verify(inviteTransaction).sendResponse(requestTerminated);
        assertNotNull(sipManage.getSipSession("cancel-reinvite-dialog"));
        sipManage.removeSipSession("cancel-reinvite-dialog");
    }

    @Test
    void processCancel_shouldSend481WhenNoInviteTransactionExists() throws Exception {
        HandleCancel handleCancel = new HandleCancel();
        SipManage sipManage = new SipManage();
        SipContext sipContext = mock(SipContext.class);
        MessageFactory messageFactory = mock(MessageFactory.class);
        sipContext.messageFactory = messageFactory;
        setField(handleCancel, "sipManage", sipManage);
        setField(handleCancel, "sipContext", sipContext);

        Request cancelRequest = mock(Request.class);
        RequestEvent cancelEvent = mock(RequestEvent.class);
        SIPServerTransaction cancelTransaction = mock(SIPServerTransaction.class);
        Response response481 = mock(Response.class);

        when(cancelEvent.getRequest()).thenReturn(cancelRequest);
        when(cancelEvent.getServerTransaction()).thenReturn(cancelTransaction);
        when(cancelTransaction.getCanceledInviteTransaction()).thenReturn(null);
        when(messageFactory.createResponse(Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST, cancelRequest)).thenReturn(response481);

        handleCancel.processCancel(cancelEvent);

        verify(cancelTransaction).sendResponse(response481);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

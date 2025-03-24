package com.example.easymrcp.mrcp;

import org.mrcp4j.MrcpEventName;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.MrcpServerMessage;
import org.mrcp4j.message.header.MrcpHeader;
import org.mrcp4j.message.header.MrcpHeaderName;

public class MyMrcpEvent extends MrcpEvent {
    public void setContent(String contentType, String contentId, String content) {
        if (content == null || (content = content.trim()).length() < 1) {
            throw new IllegalArgumentException(
                    "Cannot add zero length or null content, to remove content use removeContent() instead!");
        }

        if (contentType == null || (contentType = contentType.trim()).length() < 1) {
            throw new IllegalArgumentException(
                    "contentType is a required parameter, must not be null or zero length");
        }

        if (contentId != null && (contentId = contentId.trim()).length() < 1) {
            contentId = null;
        }

        content = content.concat(CRLF);
        int contentLength = content.length();

        // construct applicable headers
        MrcpHeader contentTypeHeader = MrcpHeaderName.CONTENT_TYPE.constructHeader(contentType);
        MrcpHeader contentIdHeader = (contentId == null) ? null : MrcpHeaderName.CONTENT_ID.constructHeader(contentId);
        MrcpHeader contentLengthHeader = MrcpHeaderName.CONTENT_LENGTH.constructHeader(new Integer(contentLength));

        // clean-up any old headers
        removeHeader(MrcpHeaderName.CONTENT_TYPE);
        removeHeader(MrcpHeaderName.CONTENT_ID);
        removeHeader(MrcpHeaderName.CONTENT_LENGTH);

        // add new headers
        addHeader(contentTypeHeader);
        addHeader(contentIdHeader);
        addHeader(contentLengthHeader);
        setContent(content);
    }
}

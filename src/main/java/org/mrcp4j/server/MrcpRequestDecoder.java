/*
 * MRCP4J - Java API implementation of MRCPv2 specification
 *
 * Copyright (C) 2005-2006 SpeechForge - http://www.speechforge.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307, USA.
 *
 * Contact: ngodfredsen@users.sourceforge.net
 *
 */
package org.mrcp4j.server;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.protocol.ProtocolDecoder;
import org.apache.mina.protocol.ProtocolDecoderOutput;
import org.apache.mina.protocol.ProtocolSession;
import org.apache.mina.protocol.ProtocolViolationException;
import org.mrcp4j.message.header.IllegalValueException;
import org.mrcp4j.message.header.MrcpHeader;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.message.request.MrcpRequest;
import org.mrcp4j.message.request.MrcpRequestFactory;

/**
 * Decodes request messages received in MRCPv2 format into {@link MrcpRequest} instances.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class MrcpRequestDecoder implements ProtocolDecoder {

	private static Logger _log = LogManager.getLogger(MrcpRequestDecoder.class);

    private StringBuilder decodeBuf = new StringBuilder();
    private StringBuilder messageBuffer = new StringBuilder();

    // 高性能字节缓冲区
    private byte[] buffer = new byte[8192];
    private int bufferLen = 0;

    @Override
    public void decode(ProtocolSession session, ByteBuffer in, ProtocolDecoderOutput out)
            throws ProtocolViolationException {
        // 1. 追加新到的数据到缓冲区
        int inLen = in.remaining();
        if (bufferLen + inLen > buffer.length) {
            // 扩容
            byte[] newBuf = new byte[(bufferLen + inLen) * 2];
            System.arraycopy(buffer, 0, newBuf, 0, bufferLen);
            buffer = newBuf;
        }
        in.get(buffer, bufferLen, inLen);
        bufferLen += inLen;

        int offset = 0;
        while (true) {
            // 1. 找请求行
            int reqLineEnd = findCRLF(buffer, offset, bufferLen);
            if (reqLineEnd == -1) break;
            String requestLine = new String(buffer, offset, reqLineEnd - offset).trim();
            if (requestLine.isEmpty()) {
                offset = reqLineEnd + 1;
                continue;
            }

            // 2. 找头部结尾，严格区分分隔符长度
            int headerEnd = -1;
            int sepLen = -1;
            for (int i = reqLineEnd + 1; i < bufferLen - 3; i++) {
                if (buffer[i] == '\r' && buffer[i+1] == '\n' && buffer[i+2] == '\r' && buffer[i+3] == '\n') {
                    headerEnd = i;
                    sepLen = 4;
                    break;
                }
            }
            if (headerEnd == -1) {
                for (int i = reqLineEnd + 1; i < bufferLen - 1; i++) {
                    if (buffer[i] == '\n' && buffer[i+1] == '\n') {
                        headerEnd = i;
                        sepLen = 2;
                        break;
                    }
                }
            }
            if (headerEnd == -1) break;

            String headersPart = new String(buffer, reqLineEnd + 1, headerEnd - (reqLineEnd + 1));
            String[] headerLines = headersPart.split("\r?\n");
            int contentLength = 0;
            for (String line : headerLines) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String name = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();
                    if ("Content-Length".equalsIgnoreCase(name)) {
                        try {
                            contentLength = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            contentLength = 0;
                        }
                    }
                }
            }

            int bodyStart = headerEnd + sepLen;
            if (bufferLen < bodyStart + contentLength) break;

            // 调试日志
            _log.debug("requestLine=[" + requestLine + "] bodyStart=" + bodyStart + " contentLength=" + contentLength + " bufferLen=" + bufferLen + " offset=" + offset);

            String body = contentLength > 0 ? new String(buffer, bodyStart, contentLength, java.nio.charset.StandardCharsets.UTF_8) : null;

            try {
                MrcpRequest request = createRequest(requestLine);
                for (String line : headerLines) {
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        String name = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        MrcpHeader header = MrcpHeaderName.createHeader(name, value);
                        request.addHeader(header);
                    }
                }
                if (contentLength > 0) {
                    request.setContent(body);
                }
                out.write(request);
            } catch (Exception e) {
                _log.error("Failed to decode MRCP message: " + e.getMessage(), e);
            }

            // offset严格跳到body结尾
            offset = bodyStart + contentLength;
        }

        // 剩余未处理数据前移
        if (offset > 0) {
            System.arraycopy(buffer, offset, buffer, 0, bufferLen - offset);
            bufferLen -= offset;
        }
    }

    // 辅助方法：查找单个CRLF
    private int findCRLF(byte[] buf, int start, int end) {
        for (int i = start; i < end - 1; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n') return i;
            if (buf[i] == '\n') return i;
        }
        return -1;
    }

    // 辅助方法：查找双CRLF
    private int findDoubleCRLF(byte[] buf, int start, int end) {
        for (int i = start; i < end - 3; i++) {
            if ((buf[i] == '\r' && buf[i + 1] == '\n' && buf[i + 2] == '\r' && buf[i + 3] == '\n') ||
                (buf[i] == '\n' && buf[i + 1] == '\n')) {
                return i;
            }
        }
        return -1;
    }

    private String readLine(ByteBuffer in) {
        if (!in.hasRemaining()) {
            return null;
        }

        decodeBuf.delete(0, decodeBuf.length());
        boolean done = false;
        do {
            byte b = in.get();
            switch(b) {
            case '\r':
                break;
            case '\n':
                done = true;
                break;
            default:
                decodeBuf.append((char) b);
            }
        } while (!done && in.hasRemaining());

        return decodeBuf.toString();
    }

    private static final int REQUEST_LINE_MRCP_VERSION_PART   = 0;
    private static final int REQUEST_LINE_MESSAGE_LENGTH_PART = 1;
    private static final int REQUEST_LINE_METHOD_NAME_PART    = 2;
    private static final int REQUEST_LINE_REQUEST_ID_PART     = 3;
    private static final int REQUEST_LINE_PART_COUNT          = 4;

    public static MrcpRequest createRequest(String requestLine) throws ParseException {

        if (requestLine == null || (requestLine = requestLine.trim()).length() < 1) {
            throw new ParseException("No request-line provided!", -1);
        }

        String[] requestLineParts = requestLine.split(" ");
        if (requestLineParts.length != REQUEST_LINE_PART_COUNT) {
            _log.error("Bad request line: [" + requestLine + "]");
            throw new ParseException("Incorrect request-line format!", -1);
        }

        MrcpRequest request = null;

        // construct request from method-name
        try {
            request = MrcpRequestFactory.createRequest(requestLineParts[REQUEST_LINE_METHOD_NAME_PART]);
        } catch (IllegalArgumentException e){
            String message = "Incorrect method-name format!";
            throw (ParseException) new ParseException(message, -1).initCause(e);
        }

        // mrcp-version
        request.setVersion(requestLineParts[REQUEST_LINE_MRCP_VERSION_PART]);  //TODO: need to check here if version is supported, or maybe at higher level...

        // message-length
        try {
            request.setMessageLength(
                Integer.parseInt(requestLineParts[REQUEST_LINE_MESSAGE_LENGTH_PART])
            );
        } catch (NumberFormatException e){
            String message = "Incorrect message-length format!";
            throw (ParseException) new ParseException(message, -1).initCause(e);
        }

        // request-id
        try {
            request.setRequestID(
                Long.parseLong(requestLineParts[REQUEST_LINE_REQUEST_ID_PART])
            );
        } catch (NumberFormatException e){
            String message = "Incorrect request-id format!";
            throw (ParseException) new ParseException(message, -1).initCause(e);
        }

        return request;
    }


}

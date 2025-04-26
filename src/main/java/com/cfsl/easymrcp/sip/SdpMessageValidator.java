/*
 * Cairo - Open source framework for control of speech media resources.
 *
 * Copyright (C) 2005-2006 SpeechForge - http://www.speechforge.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Contact: ngodfredsen@users.sourceforge.net
 *
 */
package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.sdp.SdpMessage;
import lombok.extern.slf4j.Slf4j;
import org.mrcp4j.MrcpResourceType;

import javax.sdp.*;
import java.util.Vector;

/**
 * Validates the SDP message.  Throws exception if the message is invalid.  
 * 
 * @author Spencer Lord 
 * @author Dirk Schnelle-Walka
 */
@Slf4j
public class SdpMessageValidator {

    /**
     * Validates the provided SDP message.
     * @param message the message to validate
     * @throws SdpException error validating the message
     */
    public static void validate(SdpMessage message)  throws SdpException {
        final SessionDescription sd = message.getSessionDescription();
        final StringBuilder errors = new StringBuilder();
        int problemCount = 0;
        try {
            problemCount += validateOrigin(sd, errors);
            problemCount += validateConnection(sd, errors);
            problemCount += validateMediaDescriptions(sd, errors);
        } catch (SdpException e) {
            log.warn(e.getMessage(), e);
            throw e;
        } 
        if (problemCount > 0) {
            log.warn("The following " + problemCount +
                " validation problems were found in the SDP message");
            log.warn(errors.toString());
            log.warn(sd.toString());
            throw new SdpException(errors.toString());
        }
        if (log.isDebugEnabled()) {
            log.debug("no problems in SDP message found");
        }
    }

    /**
     * Checks all available media descriptors
     * @param errors error message
     * @return number of errors found
     * @throws SdpException error accessing SDP message attributes
     * @throws SdpParseException error parsing the SDP message
     */
    private static int validateMediaDescriptions(final SessionDescription sd,
            final StringBuilder errors)
            throws SdpException, SdpParseException {
        int problemCount = 0;
        @SuppressWarnings("unchecked")
        final Vector<MediaDescription> descriptions = sd.getMediaDescriptions(true);
        for (MediaDescription md : descriptions) {
            final Media media = md.getMedia();
            final String mediaType = media.getMediaType();
            final String protocol = media.getProtocol();
            if (mediaType.equals("audio") && ((protocol.equals(SdpConstants.RTP_AVP) || protocol.equals("RTP/AVPF")))) {
                log.warn("protocol '" + protocol + "' not implemented, yet");
                   // TODO: Check if the RTP Encoding in the request is supported by cairos codecs and streaming reosurces. 
                   //       Should offers be rejected if encoding not supported -- or counter-offered?  Maybe this is not a validation task
                   //       but a session negotiation task.
            } else if (mediaType.equals("application")
                    && protocol.equals("TCP/MRCPv2")) {
                problemCount += validateApplicationMRCPv2(md, errors);
            } else {
                if (errors.length() != 0) {
                    errors.append(System.lineSeparator());
                }
                errors.append("Unrecognized media type/protocol pair in sdp message. type = ");
                errors.append(mediaType);
                errors.append(" proto= ");
                errors.append(protocol);
                problemCount++;
            }
        }
        return problemCount;
    }

    /**
     * Checks the settings for application tcp/mrcpv2
     * @param media the media description
     * @param errors error message
     * @return number of errors found
     * @throws SdpException error accessing SDP message attributes
     */
    private static int validateApplicationMRCPv2(final MediaDescription media,
            final StringBuilder errors) throws SdpParseException {
        int problemCount = 0;
        @SuppressWarnings("unchecked")
        Vector<Attribute> attributes = media.getAttributes(true);
        for (Attribute attribute : attributes) {
            if (attribute.getName().equals("setup")) {
                // value should be "active" in request and "passive"
                // in response
            } else if (attribute.getName().equals("connection")) {
                // can either be new or existing
            } else if (attribute.getName().equals("channel")) {
                if (attribute.getValue().endsWith(MrcpResourceType.SPEECHRECOG.toString())) {
                    //supported
                } else if (attribute.getValue().endsWith(MrcpResourceType.SPEECHSYNTH.toString())) {
                    //supported
                } else if (attribute.getValue().endsWith(MrcpResourceType.RECORDER.toString())) {
                   //supported
                } else if (attribute.getValue().endsWith(MrcpResourceType.DTMFRECOG.toString())) {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Cairo does not support dtmfrecog resource.");
                    problemCount++;
                } else if (attribute.getValue().endsWith(MrcpResourceType.BASICSYNTH.toString())) {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Cairo does not support basicsynth resource.");
                    problemCount++;
                } else if (attribute.getValue().endsWith(MrcpResourceType.SPEAKVERIFY.toString())) {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Cairo does not support speakverify resource.");
                    problemCount++;
                } else {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Invalid Resource type: ");
                    errors.append(attribute.getValue());
                    problemCount++;
                }
            } else if (attribute.getName().equals("cmid")) {
                // the value matches the media channel that this
                // channel is controlling
            } else if (attribute.getName().equals("resource")) {
                // in the request. The values can be either
                // speechrecog or speechsynth
                if (attribute.getValue().equals(MrcpResourceType.SPEECHRECOG.toString())) {
                    //supported
                } else if (attribute.getValue().equals(MrcpResourceType.SPEECHSYNTH.toString())) {
                    //supported
                } else if (attribute.getValue().equals(MrcpResourceType.BASICSYNTH.toString())) {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Cairo does not support basicsynth resource.");
                    problemCount++;
                } else if (attribute.getValue().equals(MrcpResourceType.SPEAKVERIFY.toString())) {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Cairo does not support speakverify resource.");
                    problemCount++;
                } else if (attribute.getValue().equals(MrcpResourceType.RECORDER.toString())) {
                    //supported
                } else if (attribute.getValue().equals(MrcpResourceType.DTMFRECOG.toString())) {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Cairo does not support dtmfrecog resource.");
                    problemCount++;
                } else if (attribute.getValue().equals(MrcpResourceType.BASICSYNTH.toString())) {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Cairo does not support basicsynth resource.");
                    problemCount++;
                } else if (attribute.getValue().equals(MrcpResourceType.SPEAKVERIFY.toString())) {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Cairo does not support speakverify resource.");
                    problemCount++;
                } else {
                    if (errors.length() != 0) {
                        errors.append(System.lineSeparator());
                    }
                    errors.append("Invalid Resource type: ");
                    errors.append(attribute.getValue());
                    problemCount++;
                }
            } else {
                //no validation for this attribute
            }
        }
        return problemCount;
    }
    
    /**
     * Checks the presence of an origin attribute.
     * @param description the session description
     * @param errors error message
     * @return number of errors found
     * @throws SdpException error accessing SDP message attributes
     */
    private static int validateOrigin(final SessionDescription description,
            final StringBuilder errors) {
        final Origin origin = description.getOrigin();
        if (origin == null) {
            if (errors.length() != 0) {
                errors.append(System.lineSeparator());
            }
            errors.append("No origin line");
            return 1;
        }
        return 0;
    }
    
    /**
     * Checks the presence of connection information.
     * @param description the session description
     * @param errors error message
     * @return number of errors found
     * @throws SdpException error accessing SDP message attributes
     */
    private static int validateConnection(final SessionDescription description,
            final StringBuilder errors) throws SdpException {
        final Connection sessionConnection = description.getConnection();
        if (sessionConnection != null) {
            return 0;
        }
        // If no general connection is provided, each media descriptor should own one
        int errorCount = 0;
        @SuppressWarnings("unchecked")
        final Vector<MediaDescription> descriptions = 
            description.getMediaDescriptions(true);
        for (MediaDescription md : descriptions) {
            final Connection connection = md.getConnection();
            if (connection == null) {
                final Media media = md.getMedia();
                if (errors.length() != 0) {
                    errors.append(System.lineSeparator());
                }
                errors.append("No connection for media ");
                errors.append(media.getMediaType());
                errors.append('.');
            }
        }
        return errorCount;
    }
}

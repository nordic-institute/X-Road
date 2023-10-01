/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.opmonitoring;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RepresentedParty;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

/**
 * The constants and operations used for representing and processing
 * operational monitoring data. Operational monitoring data are gathered when
 * XRoad requests are handled by the client and server proxies.
 */
@ToString
public class OpMonitoringData {

    // The following fields correspond to the schema defined in
    // src/op-monitor-daemon/src/main/resources/store_operational_data_request_schema.yaml .
    // Refer to the schema for detailed documentation.

    public static final String SECURITY_SERVER_INTERNAL_IP =
            "securityServerInternalIp";

    public static final String CLIENT_SECURITY_SERVER_ADDRESS =
            "clientSecurityServerAddress";
    public static final String SERVICE_SECURITY_SERVER_ADDRESS =
            "serviceSecurityServerAddress";

    public static final String CLIENT_XROAD_INSTANCE = "clientXRoadInstance";
    public static final String CLIENT_MEMBER_CLASS = "clientMemberClass";
    public static final String CLIENT_MEMBER_CODE = "clientMemberCode";
    public static final String CLIENT_SUBSYSTEM_CODE = "clientSubsystemCode";

    public static final String SERVICE_XROAD_INSTANCE = "serviceXRoadInstance";
    public static final String SERVICE_MEMBER_CLASS = "serviceMemberClass";
    public static final String SERVICE_MEMBER_CODE = "serviceMemberCode";
    public static final String SERVICE_SUBSYSTEM_CODE = "serviceSubsystemCode";

    private static final String SERVICE_CODE = "serviceCode";
    private static final String SERVICE_VERSION = "serviceVersion";

    private static final String SECURITY_SERVER_TYPE = "securityServerType";

    // Unix timestamps in milliseconds.
    private static final String REQUEST_IN_TIMESTAMP = "requestInTs";
    private static final String REQUEST_OUT_TIMESTAMP = "requestOutTs";
    private static final String RESPONSE_IN_TIMESTAMP = "responseInTs";
    private static final String RESPONSE_OUT_TIMESTAMP = "responseOutTs";

    private static final String REPRESENTED_PARTY_CLASS =
            "representedPartyClass";
    private static final String REPRESENTED_PARTY_CODE =
            "representedPartyCode";

    private static final String MESSAGE_ID = "messageId";
    private static final String MESSAGE_USER_ID = "messageUserId";
    private static final String MESSAGE_ISSUE = "messageIssue";
    private static final String MESSAGE_PROTOCOL_VERSION =
            "messageProtocolVersion";

    private static final String X_REQUEST_ID = "xRequestId";
    private static final String REQUEST_MIME_SIZE = "requestMimeSize";

    private static final String REQUEST_ATTACHMENT_COUNT =
            "requestAttachmentCount";

    private static final String RESPONSE_MIME_SIZE = "responseMimeSize";
    private static final String REQUEST_SIZE = "requestSize";
    private static final String RESPONSE_SIZE = "responseSize";
    private static final String RESPONSE_ATTACHMENT_COUNT =
            "responseAttachmentCount";

    private static final String SUCCEEDED = "succeeded";
    private static final String REST_RESPONSE_STATUS_CODE = "statusCode";

    private static final String SOAP_FAULT_CODE = "faultCode";
    private static final String SOAP_FAULT_STRING = "faultString";
    private static final String SERVICE_TYPE = "serviceType";

    /**
     * The supported types of security servers in the context of operational
     * monitoring.
     */
    public enum SecurityServerType {
        CLIENT("Client"),
        PRODUCER("Producer");

        @Getter
        private String typeString;

        SecurityServerType(String typeString) {
            this.typeString = typeString;
        }

        /**
         * Returns SecurityServerType by type string.
         * @param type type string
         * @return SecurityServerType or null if given type string is invalid
         */
        public static SecurityServerType fromString(String type) {
            if (type != null) {
                for (SecurityServerType t : SecurityServerType.values()) {
                    if (type.equals(t.typeString)) {
                        return t;
                    }
                }
            }

            return null;
        }
    }

    @Setter
    /**
     * In case true, the same value as "response out" is assigned
     * to the "response in" also. This is the case if the SOAP message is handled
     * by a single security server (metadata and monitoring requests).
     */
    private boolean assignResponseOutTsToResponseInTs = false;

    private final Map<String, Object> data = new HashMap<>();

    /**
     * Constructor for creating an instance in code that handles incoming
     * XRoad requests.
     * @param type        security server type
     * @param requestInTs the timestamp of handling the XRoad request
     */
    public OpMonitoringData(SecurityServerType type, long requestInTs) {
        setSecurityServerType(type);
        setRequestInTs(requestInTs);
        setSucceeded(false);
    }

    /**
     * Returns map of the operational monitoring data.
     * @return operational monitoring data
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Sets the security server type.
     * @param type security server type
     */
    void setSecurityServerType(SecurityServerType type) {
        data.put(SECURITY_SERVER_TYPE, type.getTypeString());
    }

    /**
     * Sets the security server internal IP address.
     * @param internalIp IP address
     */
    public void setSecurityServerInternalIp(String internalIp) {
        data.put(SECURITY_SERVER_INTERNAL_IP, internalIp);
    }

    /**
     * Sets the "request in" timestamp.
     * @param timestamp Unix timestamp in milliseconds
     */
    public void setRequestInTs(long timestamp) {
        data.put(REQUEST_IN_TIMESTAMP, timestamp);
    }

    public Long getRequestInTs() {
        return (Long) data.get(REQUEST_IN_TIMESTAMP);
    }

    /**
     * Sets the "request out" timestamp.
     * @param timestamp Unix timestamp in milliseconds
     */
    public void setRequestOutTs(long timestamp) {
        data.put(REQUEST_OUT_TIMESTAMP, timestamp);
    }

    /**
     * Sets the "response in" timestamp.
     * @param timestamp Unix timestamp in milliseconds
     */
    public void setResponseInTs(long timestamp) {
        data.put(RESPONSE_IN_TIMESTAMP, timestamp);
    }

    /**
     * Sets the "response out" timestamp. In case the field assignResponseOutTsToResponseInTs is
     * true, the same value is assigned to the "response in" also.
     * @param timestamp Unix timestamp in milliseconds
     * @param overwrite if true, old value is overwritten, otherwise old value remains
     */
    public void setResponseOutTs(long timestamp, boolean overwrite) {
        if (!overwrite && data.get(RESPONSE_OUT_TIMESTAMP) != null) {
            return;
        }

        if (assignResponseOutTsToResponseInTs) {
            setResponseInTs(timestamp);
        }

        data.put(RESPONSE_OUT_TIMESTAMP, timestamp);
    }

    /**
     * Sets the fields related to the client ID.
     * @param clientId client ID
     */
    public void setClientId(ClientId clientId) {
        if (clientId != null) {
            data.put(CLIENT_XROAD_INSTANCE, clientId.getXRoadInstance());
            data.put(CLIENT_MEMBER_CLASS, clientId.getMemberClass());
            data.put(CLIENT_MEMBER_CODE, clientId.getMemberCode());
            data.put(CLIENT_SUBSYSTEM_CODE, clientId.getSubsystemCode());
        }
    }

    /**
     * Sets the fields related to the service ID.
     * @param serviceId service ID
     */
    public void setServiceId(ServiceId serviceId) {
        if (serviceId != null) {
            data.put(SERVICE_XROAD_INSTANCE, serviceId.getXRoadInstance());
            data.put(SERVICE_MEMBER_CLASS, serviceId.getMemberClass());
            data.put(SERVICE_MEMBER_CODE, serviceId.getMemberCode());
            data.put(SERVICE_SUBSYSTEM_CODE, serviceId.getSubsystemCode());
            data.put(SERVICE_CODE, serviceId.getServiceCode());
            data.put(SERVICE_VERSION, serviceId.getServiceVersion());
        }
    }

    /**
     * Sets the message ID.
     * @param messageId message ID
     */
    public void setMessageId(String messageId) {
        data.put(MESSAGE_ID, messageId);
    }

    /**
     * Sets the message user ID.
     * @param messageUserId user ID
     */
    public void setMessageUserId(String messageUserId) {
        data.put(MESSAGE_USER_ID, messageUserId);
    }

    /**
     * Sets the message issue.
     * @param messageIssue issue
     */
    public void setMessageIssue(String messageIssue) {
        data.put(MESSAGE_ISSUE, messageIssue);
    }

    /**
     * Sets the fields related to the represented party.
     * @param representedParty the party represented
     */
    public void setRepresentedParty(RepresentedParty representedParty) {
        if (representedParty != null) {
            data.put(REPRESENTED_PARTY_CLASS, representedParty.getPartyClass());
            data.put(REPRESENTED_PARTY_CODE, representedParty.getPartyCode());
        }
    }

    /**
     * Sets the message protocol version.
     * @param messageProtocolVersion message protocol version
     */
    public void setMessageProtocolVersion(String messageProtocolVersion) {
        data.put(MESSAGE_PROTOCOL_VERSION, messageProtocolVersion);
    }

    /**
     * Sets client security server address.
     * @param address address
     */
    public void setClientSecurityServerAddress(String address) {
        data.put(CLIENT_SECURITY_SERVER_ADDRESS, address);
    }

    /**
     * Sets service security server address.
     * @param address address
     */
    public void setServiceSecurityServerAddress(String address) {
        data.put(SERVICE_SECURITY_SERVER_ADDRESS, address);
    }

    /**
     * Sets request size.
     * @param size request size
     */
    public void setRequestSize(long size) {
        data.put(REQUEST_SIZE, size);
    }

    /**
     * Sets response size.
     * @param size response size
     */
    public void setResponseSize(long size) {
        data.put(RESPONSE_SIZE, size);
    }

    /**
     * Sets request MIME size.
     * @param size MIME size
     */
    public void setRequestMimeSize(long size) {
        data.put(REQUEST_MIME_SIZE, size);
    }

    /**
     * Sets request attachment count.
     * @param count attachment count
     */
    public void setRequestAttachmentCount(int count) {
        data.put(REQUEST_ATTACHMENT_COUNT, count);
    }

    /**
     * Sets response MIME size
     * @param size MIME size
     */
    public void setResponseMimeSize(long size) {
        data.put(RESPONSE_MIME_SIZE, size);
    }

    /**
     * Sets response attachment count.
     * @param count attachment count
     */
    public void setResponseAttachmentCount(int count) {
        data.put(RESPONSE_ATTACHMENT_COUNT, count);
    }

    /**
     * Sets succeeded flag.
     * @param succeeded succeeded flag
     */
    public void setSucceeded(boolean succeeded) {
        data.put(SUCCEEDED, succeeded);
    }

    /**
     * Sets rest response status code
     * @param statusCode http status code for the response
     */
    public void setRestResponseStatusCode(int statusCode) {
        data.put(REST_RESPONSE_STATUS_CODE, statusCode);
    }

    /**
     * Sets a fault code and string from given CodedException.
     * @param e CodedException
     */
    public void setFaultCodeAndString(CodedException e) {
        if (e != null) {
            data.put(SOAP_FAULT_CODE, e.getFaultCode());
            data.put(SOAP_FAULT_STRING, e.getFaultString());
        }
    }

    /**
     * Sets x-road-request-id of the message.
     * @param xRequestId x-request-id
     */
    public void setXRequestId(String xRequestId) {
        data.put(X_REQUEST_ID, xRequestId);
    }

    /**
     * Sets service type.
     * @param serviceType service type
     */
    public void setServiceType(String serviceType) {
        data.put(SERVICE_TYPE, serviceType);
    }

}

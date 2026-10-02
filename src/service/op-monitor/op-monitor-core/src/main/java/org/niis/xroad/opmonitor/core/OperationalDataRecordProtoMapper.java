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
package org.niis.xroad.opmonitor.core;

import lombok.experimental.UtilityClass;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

@UtilityClass
class OperationalDataRecordProtoMapper {

    static OperationalDataRecord fromProto(OperationalDataRecordProto proto) {
        var record = new OperationalDataRecord();
        setIfPresent(proto::hasSecurityServerInternalIp, proto::getSecurityServerInternalIp,
                record::setSecurityServerInternalIp);
        setSecurityServerType(proto, record);
        setTimestamps(proto, record);
        setClientAndService(proto, record);
        setMessage(proto, record);
        setSizes(proto, record);
        setOutcome(proto, record);
        return record;
    }

    private static void setSecurityServerType(OperationalDataRecordProto proto, OperationalDataRecord record) {
        var typeString = proto.hasSecurityServerType() ? toTypeString(proto.getSecurityServerType()) : null;
        if (typeString != null) {
            record.setSecurityServerType(typeString);
        }
    }

    private static void setTimestamps(OperationalDataRecordProto proto, OperationalDataRecord record) {
        setIfPresent(proto::hasRequestInTs, proto::getRequestInTs, record::setRequestInTs);
        setIfPresent(proto::hasRequestOutTs, proto::getRequestOutTs, record::setRequestOutTs);
        setIfPresent(proto::hasResponseInTs, proto::getResponseInTs, record::setResponseInTs);
        setIfPresent(proto::hasResponseOutTs, proto::getResponseOutTs, record::setResponseOutTs);
    }

    private static void setClientAndService(OperationalDataRecordProto proto, OperationalDataRecord record) {
        setIfPresent(proto::hasClientXroadInstance, proto::getClientXroadInstance, record::setClientXRoadInstance);
        setIfPresent(proto::hasClientMemberClass, proto::getClientMemberClass, record::setClientMemberClass);
        setIfPresent(proto::hasClientMemberCode, proto::getClientMemberCode, record::setClientMemberCode);
        setIfPresent(proto::hasClientSubsystemCode, proto::getClientSubsystemCode, record::setClientSubsystemCode);
        setIfPresent(proto::hasServiceXroadInstance, proto::getServiceXroadInstance, record::setServiceXRoadInstance);
        setIfPresent(proto::hasServiceMemberClass, proto::getServiceMemberClass, record::setServiceMemberClass);
        setIfPresent(proto::hasServiceMemberCode, proto::getServiceMemberCode, record::setServiceMemberCode);
        setIfPresent(proto::hasServiceSubsystemCode, proto::getServiceSubsystemCode, record::setServiceSubsystemCode);
        setIfPresent(proto::hasServiceCode, proto::getServiceCode, record::setServiceCode);
        setIfPresent(proto::hasServiceVersion, proto::getServiceVersion, record::setServiceVersion);
        setIfPresent(proto::hasServiceType, proto::getServiceType, record::setServiceType);
        setIfPresent(proto::hasClientSecurityServerAddress, proto::getClientSecurityServerAddress,
                record::setClientSecurityServerAddress);
        setIfPresent(proto::hasServiceSecurityServerAddress, proto::getServiceSecurityServerAddress,
                record::setServiceSecurityServerAddress);
        setIfPresent(proto::hasRepresentedPartyClass, proto::getRepresentedPartyClass, record::setRepresentedPartyClass);
        setIfPresent(proto::hasRepresentedPartyCode, proto::getRepresentedPartyCode, record::setRepresentedPartyCode);
    }

    private static void setMessage(OperationalDataRecordProto proto, OperationalDataRecord record) {
        setIfPresent(proto::hasRestMethod, proto::getRestMethod, record::setRestMethod);
        setIfPresent(proto::hasRestPath, proto::getRestPath, record::setRestPath);
        setIfPresent(proto::hasXroadVersion, proto::getXroadVersion, record::setXRoadVersion);
        setIfPresent(proto::hasMessageId, proto::getMessageId, record::setMessageId);
        setIfPresent(proto::hasMessageUserId, proto::getMessageUserId, record::setMessageUserId);
        setIfPresent(proto::hasMessageIssue, proto::getMessageIssue, record::setMessageIssue);
        setIfPresent(proto::hasMessageProtocolVersion, proto::getMessageProtocolVersion, record::setMessageProtocolVersion);
        setIfPresent(proto::hasXRequestId, proto::getXRequestId, record::setXRequestId);
    }

    private static void setSizes(OperationalDataRecordProto proto, OperationalDataRecord record) {
        setIfPresent(proto::hasRequestSize, proto::getRequestSize, record::setRequestSize);
        setIfPresent(proto::hasResponseSize, proto::getResponseSize, record::setResponseSize);
        setIfPresent(proto::hasRequestMimeSize, proto::getRequestMimeSize, record::setRequestMimeSize);
        setIfPresent(proto::hasResponseMimeSize, proto::getResponseMimeSize, record::setResponseMimeSize);
        setIfPresent(proto::hasRequestAttachmentCount, proto::getRequestAttachmentCount, record::setRequestAttachmentCount);
        setIfPresent(proto::hasResponseAttachmentCount, proto::getResponseAttachmentCount, record::setResponseAttachmentCount);
    }

    private static void setOutcome(OperationalDataRecordProto proto, OperationalDataRecord record) {
        setIfPresent(proto::hasSucceeded, proto::getSucceeded, record::setSucceeded);
        setIfPresent(proto::hasStatusCode, proto::getStatusCode, record::setStatusCode);
        setIfPresent(proto::hasFaultCode, proto::getFaultCode, record::setFaultCode);
        setIfPresent(proto::hasFaultString, proto::getFaultString, record::setFaultString);
    }

    private static String toTypeString(org.niis.xroad.opmonitor.api.SecurityServerType type) {
        return switch (type) {
            case CLIENT -> OpMonitoringData.SecurityServerType.CLIENT.getTypeString();
            case PRODUCER -> OpMonitoringData.SecurityServerType.PRODUCER.getTypeString();
            case SECURITY_SERVER_TYPE_UNSPECIFIED, UNRECOGNIZED -> null;
        };
    }

    private static <T> void setIfPresent(BooleanSupplier present, Supplier<T> value, Consumer<T> setter) {
        if (present.getAsBoolean()) {
            setter.accept(value.get());
        }
    }
}

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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RepresentedParty;
import ee.ria.xroad.common.util.JsonUtils;

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.opmonitor.api.OpMonitoringData;

import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.OBJECT_READER;

@UtilityClass
class OpMonitoringDataFixtures {

    static OpMonitoringData fullyPopulated(OpMonitoringData.SecurityServerType type) {
        var data = new OpMonitoringData(type, 1_700_000_000_000L);
        data.setSecurityServerInternalIp("10.0.0.1");
        data.setRequestOutTs(1_700_000_000_100L);
        data.setResponseInTs(1_700_000_000_400L);
        data.setResponseOutTs(1_700_000_000_500L, false);
        data.setClientId(ClientId.Conf.create("DEV", "COM", "222", "TestClient"));
        data.setServiceId(ServiceId.Conf.create("DEV", "COM", "222", "TestService", "getRandom", "v1"));
        data.setRestMethod("POST");
        data.setRestPath("/r1/DEV/COM/222/TestService/getRandom");
        data.setXRoadVersion("8.0.0");
        data.setRepresentedParty(new RepresentedParty("COM", "333"));
        data.setMessageId("message-id");
        data.setMessageUserId("user-id");
        data.setMessageIssue("issue");
        data.setMessageProtocolVersion("4.0");
        data.setClientSecurityServerAddress("ss0.example.org");
        data.setServiceSecurityServerAddress("ss1.example.org");
        data.setRequestSize(1024);
        data.setResponseSize(2048);
        data.setRequestMimeSize(3072);
        data.setRequestAttachmentCount(1);
        data.setResponseMimeSize(4096);
        data.setResponseAttachmentCount(2);
        data.setSucceeded(true);
        data.setFaultCodeAndString(XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR).details("Fault details").build());
        data.setXRequestId("8a2f2e10-8ffb-40c3-b446-aebd23fd9248");
        data.setRestResponseStatusCode(201);
        data.setServiceType("REST");
        return data;
    }
    static OpMonitoringData withOversizedText(OpMonitoringData.SecurityServerType type) {
        var data = fullyPopulated(type);
        String longText = "\u20ac".repeat(300);
        data.setSecurityServerInternalIp(longText);
        data.setClientId(ClientId.Conf.create(longText, longText, longText, longText));
        data.setServiceId(ServiceId.Conf.create(longText, longText, longText, longText, longText, longText));
        data.setRestMethod(longText);
        data.setRestPath(longText);
        data.setXRoadVersion(longText);
        data.setRepresentedParty(new RepresentedParty(longText, longText));
        data.setMessageId(longText);
        data.setMessageUserId("u".repeat(5 * 1024 * 1024));
        data.setMessageIssue(longText);
        data.setMessageProtocolVersion(longText);
        data.setClientSecurityServerAddress(longText);
        data.setServiceSecurityServerAddress(longText);
        data.setFaultCodeAndString(XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR)
                .details("\u20ac".repeat(3000)).build());
        data.setXRequestId(longText);
        data.setServiceType(longText);
        return data;
    }

    static OperationalDataRecord viaRestJson(OpMonitoringData data) {
        String json = JsonUtils.getObjectWriter().writeValueAsString(data.getData());
        return OBJECT_READER.forType(OperationalDataRecord.class).readValue(json);
    }
}

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
package org.niis.xroad.cs.test;

import io.restassured.response.Response;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.common.managemenetrequest.test.TestGenericClientRequestBuilder;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;

import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_RECEIVER;
import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_SERVER_ID;

/**
 * 080 - Management requests API: security server maintenance mode enable. Ordered after
 * {@link ClientRenameIntTest} - the other {@code 080}-prefixed retired feature - matching the two
 * features' alphabetical filename order ("client-rename" before "maintenance-mode-enable").
 */
@Order(81)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class MaintenanceModeEnableIntTest extends AbstractManagementServiceIntTest {

    @Test
    @Order(1)
    @DisplayName("Enable maintenance mode request is successful")
    void enableMaintenanceModeRequestIsSuccessful() throws Exception {
        adminApiIsMocked(202, ManagementRequestTypeDto.MAINTENANCE_MODE_ENABLE_REQUEST, 1122);

        var response = executeMaintenanceModeEnableRequest("EE:CLASS:MEMBER", DEFAULT_SERVER_ID.asEncodedId(), "I'll be back");

        assertResponseAndRequestId(response, 200, 1122);
        assertAdminApiReceivedRequest("""
                {
                "type" : "MAINTENANCE_MODE_ENABLE_REQUEST",
                "origin" : "SECURITY_SERVER",
                "security_server_id" : "EE:CLASS:MEMBER:SS1",
                "message" : "I'll be back"
                }
                """);
    }

    @Test
    @Order(2)
    @DisplayName("Enable maintenance mode request fails with soap fault when request sender is not server owner")
    void enableMaintenanceModeRequestFailsWhenSenderIsNotServerOwner() throws Exception {
        var response = executeMaintenanceModeEnableRequest("EE:CLASS:MEMBER", "EE:CLASS:MEMBER2:SS1", "I'll be back");

        assertSoapFaultCodeAndString(response, 500, "invalid_request", "Sender does not match server owner.");
        assertAdminApiReceivedNoRequest();
    }

    private Response executeMaintenanceModeEnableRequest(String clientIdStr, String serverIdStr, String message) throws Exception {
        var clientId = resolveClientIdFromEncodedStr(clientIdStr);
        var request = TestGenericClientRequestBuilder.newBuilder()
                .withSenderClientId(clientId)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withServerId(resolveServerIdFromEncodedStr(serverIdStr))
                .withClientId(clientId)
                .withClientOcsp(CertificateStatus.GOOD)
                .withSoapMessageBuilder((builder, serverId, reqClientId) -> builder.buildMaintenanceModeEnableRequest(serverId, message))
                .build();
        return executeRequest(request.createPayload());
    }
}

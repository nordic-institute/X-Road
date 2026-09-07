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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.common.managemenetrequest.test.TestSimpleManagementRequestBuilder;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;

import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_RECEIVER;
import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_SERVER_ID;

/**
 * 030 - Management requests API: client deletion.
 */
@Order(30)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class ClientDeletionIntTest extends AbstractManagementServiceIntTest {

    @Test
    @Order(1)
    @DisplayName("Client deletion request is successful")
    void clientDeletionRequestIsSuccessful() throws Exception {
        adminApiIsMocked(202, ManagementRequestTypeDto.CLIENT_DELETION_REQUEST, 1122);

        var response = executeClientDeletionRequest("EE:CLASS:MEMBER", DEFAULT_SERVER_ID.asEncodedId());

        assertResponseAndRequestId(response, 200, 1122);
        assertAdminApiReceivedRequest("""
                {
                "type" : "CLIENT_DELETION_REQUEST",
                "origin" : "SECURITY_SERVER",
                "security_server_id" : "EE:CLASS:MEMBER:SS1",
                "client_id" : "EE:CLASS:MEMBER"
                }
                """);
    }

    @Test
    @Order(2)
    @DisplayName("Client deletion fails with soap fault when request sender is not server owner")
    void clientDeletionFailsWhenSenderIsNotServerOwner() throws Exception {
        var response = executeClientDeletionRequest("EE:CLASS:MEMBER", "EE:CLASS:MEMBER2:SS1");

        assertSoapFaultCodeAndString(response, 500, "invalid_request", "Sender does not match server owner.");
        assertAdminApiReceivedNoRequest();
    }

    private Response executeClientDeletionRequest(String clientIdStr, String serverIdStr) throws Exception {
        var clientId = resolveClientIdFromEncodedStr(clientIdStr);
        var securityServerId = resolveServerIdFromEncodedStr(serverIdStr);
        var request = TestSimpleManagementRequestBuilder.newBuilder()
                .withSenderClientId(clientId)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withSoapMessageBuilder((keyPairGenerator, builder) -> builder.buildClientDeletionRequest(securityServerId, clientId))
                .build();
        return executeRequest(request.createPayload());
    }
}

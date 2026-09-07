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

import ee.ria.xroad.common.util.TimeUtils;

import io.restassured.response.Response;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.common.managemenetrequest.test.TestGenericClientRequest;
import org.niis.xroad.common.managemenetrequest.test.TestGenericClientRequestBuilder;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestBuilder;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestPayload;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;

import java.util.Date;

import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_RECEIVER;
import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_SERVER_ID;

/**
 * 010 - Management requests API: client registration.
 */
@Order(10)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class ClientRegistrationIntTest extends AbstractManagementServiceIntTest {

    @Test
    @Order(1)
    @DisplayName("Client registration is successful")
    void clientRegistrationIsSuccessful() throws Exception {
        adminApiIsMocked(202, ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST, 1122);

        var response = executeRegistrationRequest(null, "EE:CLASS:MEMBER", DEFAULT_SERVER_ID.asEncodedId());

        assertResponseAndRequestId(response, 200, 1122);
        assertAdminApiReceivedRequest("""
                {
                "type" : "CLIENT_REGISTRATION_REQUEST",
                "origin" : "SECURITY_SERVER",
                "security_server_id" : "EE:CLASS:MEMBER:SS1",
                "client_id" : "EE:CLASS:MEMBER"
                }
                """);
    }

    @Test
    @Order(2)
    @DisplayName("Client registration is successful for subsystem")
    void clientRegistrationIsSuccessfulForSubsystem() throws Exception {
        adminApiIsMocked(202, ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST, 1133);

        var response = executeRegistrationRequest(null, "EE:CLASS:MEMBER:sub", DEFAULT_SERVER_ID.asEncodedId());

        assertResponseAndRequestId(response, 200, 1133);
        assertAdminApiReceivedRequest("""
                {
                "type" : "CLIENT_REGISTRATION_REQUEST",
                "origin" : "SECURITY_SERVER",
                "security_server_id" : "EE:CLASS:MEMBER:SS1",
                "client_id" : "EE:CLASS:MEMBER:sub"
                }
                """);
    }

    @Test
    @Order(3)
    @DisplayName("Client registration is successful for subsystem with name")
    void clientRegistrationIsSuccessfulForSubsystemWithName() throws Exception {
        adminApiIsMocked(202, ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST, 1133);

        var response = executeRegistrationRequest("Subsystem Name", "EE:CLASS:MEMBER:sub", DEFAULT_SERVER_ID.asEncodedId());

        assertResponseAndRequestId(response, 200, 1133);
        assertAdminApiReceivedRequest("""
                {
                "type" : "CLIENT_REGISTRATION_REQUEST",
                "origin" : "SECURITY_SERVER",
                "security_server_id" : "EE:CLASS:MEMBER:SS1",
                "client_id" : "EE:CLASS:MEMBER:sub",
                "subsystem_name" : "Subsystem Name"
                }
                """);
    }

    @Test
    @Order(4)
    @DisplayName("Client registration fails with soap fault on bad admin-api response")
    void clientRegistrationFailsOnBadAdminApiResponse() throws Exception {
        adminApiIsMocked(409, ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST, 1122);

        var response = executeRegistrationRequest(null, "EE:CLASS:MEMBER", DEFAULT_SERVER_ID.asEncodedId());

        assertSoapFault(response, 500);
    }

    @Test
    @Order(5)
    @DisplayName("Client registration fails with soap fault on invalid signature")
    void clientRegistrationFailsOnInvalidSignature() throws Exception {
        adminApiIsMocked(200, ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST, 1122);
        var clientId = resolveClientIdFromEncodedStr("EE:CLASS:MEMBER");

        var request = TestGenericClientRequestBuilder.newBuilder()
                .withSenderClientId(clientId)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withServerId(DEFAULT_SERVER_ID)
                .withClientId(clientId)
                .withClientOcsp(CertificateStatus.GOOD)
                .withSoapMessageBuilder(TestManagementRequestBuilder::buildClientRegRequest)
                .withRequestTypeBuilder((keyPairGenerator, clientCert, clientCertOcsp, clientKey, soapRequest) ->
                        new TestGenericClientRequest(
                                clientCert, clientCertOcsp,
                                keyPairGenerator.generateKeyPair().getPrivate(),
                                soapRequest))
                .build();

        var response = executeRequest(request.createPayload());

        assertSoapFaultCode(response, 500, "invalid_signature_value");
        assertAdminApiReceivedNoRequest();
    }

    @Test
    @Order(6)
    @DisplayName("Client registration fails with soap fault on invalid certificate")
    void clientRegistrationFailsOnInvalidCertificate() throws Exception {
        adminApiIsMocked(200, ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST, 1122);
        var clientId = resolveClientIdFromEncodedStr("EE:CLASS:MEMBER");

        var request = TestGenericClientRequestBuilder.newBuilder()
                .withSenderClientId(clientId)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withServerId(DEFAULT_SERVER_ID)
                .withClientId(clientId)
                .withClientOcsp(CertificateStatus.GOOD)
                .withSoapMessageBuilder(TestManagementRequestBuilder::buildClientRegRequest)
                .withRequestTypeBuilder((keyPairGenerator, clientCert, clientCertOcsp, clientKey, soapRequest) ->
                        new TestGenericClientRequest(new byte[0], clientCertOcsp, clientKey, soapRequest))
                .build();

        var response = executeRequest(request.createPayload());

        assertSoapFaultCode(response, 500, "incorrect_certificate");
        assertAdminApiReceivedNoRequest();
    }

    @Test
    @Order(7)
    @DisplayName("Client registration fails with soap fault on revoked ocsp certificate")
    void clientRegistrationFailsOnRevokedOcsp() throws Exception {
        adminApiIsMocked(200, ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST, 1122);
        var clientId = resolveClientIdFromEncodedStr("EE:CLASS:MEMBER");

        var request = TestGenericClientRequestBuilder.newBuilder()
                .withSenderClientId(clientId)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withServerId(DEFAULT_SERVER_ID)
                .withClientId(clientId)
                .withSoapMessageBuilder(TestManagementRequestBuilder::buildClientRegRequest)
                .withClientOcsp(new RevokedStatus(Date.from(TimeUtils.now().minusSeconds(3600)), CRLReason.unspecified))
                .build();

        var response = executeRequest(request.createPayload());

        assertSoapFaultCode(response, 500, "cert_validation");
        assertAdminApiReceivedNoRequest();
    }

    @Test
    @Order(8)
    @DisplayName("Client registration fails with soap fault on empty request")
    void clientRegistrationFailsOnEmptyRequest() {
        adminApiIsMocked(200, ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST, 1122);

        var response = executeRequest(TestManagementRequestPayload.empty());

        assertSoapFaultCode(response, 500, "invalid_request");
        assertAdminApiReceivedNoRequest();
    }

    @Test
    @Order(9)
    @DisplayName("Client registration fails with invalid clientId")
    void clientRegistrationFailsWithInvalidClientId() throws Exception {
        adminApiIsMocked(200, ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST, 1122);

        var response = executeRegistrationRequest(null, "EE:CLASS:___", DEFAULT_SERVER_ID.asEncodedId());

        assertSoapFaultCode(response, 500, "invalid_request");
        assertAdminApiReceivedNoRequest();
    }

    @Test
    @Order(10)
    @DisplayName("Client registration fails with soap fault when request sender is not server owner")
    void clientRegistrationFailsWhenSenderIsNotServerOwner() throws Exception {
        var response = executeRegistrationRequest(null, "EE:CLASS:MEMBER", "EE:CLASS:MEMBER2:SS1");

        assertSoapFaultCodeAndString(response, 500, "invalid_request", "Sender does not match server owner.");
        assertAdminApiReceivedNoRequest();
    }

    private Response executeRegistrationRequest(String subsystemName, String clientIdStr, String serverIdStr) throws Exception {
        var clientId = resolveClientIdFromEncodedStr(clientIdStr);
        var request = TestGenericClientRequestBuilder.newBuilder()
                .withSenderClientId(clientId.getMemberId())
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withServerId(resolveServerIdFromEncodedStr(serverIdStr))
                .withClientId(clientId)
                .withClientOcsp(CertificateStatus.GOOD)
                .withSoapMessageBuilder((builder, serverId, reqClientId) ->
                        builder.buildClientRegRequest(serverId, reqClientId, subsystemName))
                .build();
        return executeRequest(request.createPayload());
    }
}

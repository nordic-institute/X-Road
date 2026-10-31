/*
 * The MIT License
 *
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

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;

import io.restassured.response.Response;
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.common.managemenetrequest.test.TestGenericClientRequestBuilder;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_RECEIVER;
import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_SERVER_ID;

/**
 * 095 - Management requests API: member DID registration requests rejected as a whole.
 */
@Order(95)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class MemberDidRegistrationIntTest extends AbstractManagementServiceIntTest {
    private static final int REQUEST_SIZE_LIMIT = 100_000;
    private static final ClientId.Conf OWNER = DEFAULT_SERVER_ID.getOwner();
    private static final ClientId.Conf OTHER_MEMBER = ClientId.Conf.create("EE", "CLASS", "MEMBER2");

    @Test
    @Order(1)
    @DisplayName("Batch over the entry cap is refused with request_too_large")
    void batchOverTheEntryCapIsRefused() throws Exception {
        adminApiRejectsWith(400, "management_request_too_large");

        var response = executeMemberDidRegRequest(OWNER, OWNER, memberDids("did:web:a.example.org"));

        assertSoapFaultCode(response, 500, "request_too_large");
    }

    @Test
    @Order(2)
    @DisplayName("Body over the size limit is refused with request_too_large")
    void bodyOverTheSizeLimitIsRefused() throws Exception {
        var longDid = "did:web:" + RandomStringUtils.insecure().nextAlphanumeric(REQUEST_SIZE_LIMIT);

        var response = executeMemberDidRegRequest(OWNER, OWNER, memberDids(longDid));

        assertSoapFaultCodeAndString(response, 500, "request_too_large", "Request size limit " + REQUEST_SIZE_LIMIT + " exceeded");
        assertAdminApiReceivedNoRequest();
    }

    @Test
    @Order(3)
    @DisplayName("Request sent by a member that is not the server owner is refused")
    void senderThatIsNotTheOwnerIsRefused() throws Exception {
        var response = executeMemberDidRegRequest(OTHER_MEMBER, OTHER_MEMBER, memberDids("did:web:a.example.org"));

        assertSoapFaultCodeAndString(response, 500, "invalid_request", "Sender does not match server owner.");
        assertAdminApiReceivedNoRequest();
    }

    @Test
    @Order(4)
    @DisplayName("Request signed with a certificate of another member is refused")
    void certificateOfAnotherMemberIsRefused() throws Exception {
        var response = executeMemberDidRegRequest(OWNER, OTHER_MEMBER, memberDids("did:web:a.example.org"));

        assertSoapFaultCode(response, 500, "invalid_request");
        assertAdminApiReceivedNoRequest();
    }

    private Response executeMemberDidRegRequest(ClientId.Conf sender, ClientId.Conf certSubject, Map<ClientId.Conf, String> memberDids)
            throws Exception {
        var request = TestGenericClientRequestBuilder.newBuilder()
                .withSenderClientId(sender)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withServerId(DEFAULT_SERVER_ID)
                .withClientId(OWNER)
                .withClientOcsp(CertificateStatus.GOOD)
                .withRequestAssembler((keyPairGenerator, requestTypeBuilder, soapMessageBuilder, senderId, receiverId, serverId,
                                       clientId, ocspStatus) -> {
                    var keyPair = keyPairGenerator.generateKeyPair();
                    var cert = TestCertUtil.generateSignCert(keyPair.getPublic(), certSubject);
                    var ocsp = OcspTestUtils.createOCSPResponse(cert,
                            TestCertUtil.getCaCert(),
                            TestCertUtil.getOcspSigner().certChain[0],
                            TestCertUtil.getOcspSigner().key,
                            ocspStatus);
                    return requestTypeBuilder.build(keyPairGenerator, cert.getEncoded(), ocsp.getEncoded(), keyPair.getPrivate(),
                            soapMessageBuilder.build(
                                    new TestManagementRequestBuilder(senderId, receiverId),
                                    serverId, clientId));
                })
                .withSoapMessageBuilder((builder, serverId, clientId) -> builder.buildMemberDidRegRequest(serverId, memberDids))
                .build();
        return executeRequest(request.createPayload());
    }

    private static Map<ClientId.Conf, String> memberDids(String did) {
        var memberDids = new LinkedHashMap<ClientId.Conf, String>();
        memberDids.put(OWNER, did);
        memberDids.put(ClientId.Conf.create("EE", "CLASS", "MEMBER3"), did);
        memberDids.put(ClientId.Conf.create("EE", "CLASS", "MEMBER4"), did);
        return memberDids;
    }
}

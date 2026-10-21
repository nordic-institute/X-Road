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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessageImpl;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.managemenetrequest.test.TestAuthRegTypeRequest;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestBuilder;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;

import java.security.KeyPair;
import java.util.stream.Stream;

import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_RECEIVER;
import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_SERVER_ID;

/**
 * 090 - Management requests API: direct request types sent to the management service are refused.
 */
@Order(90)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class DirectRequestRejectionIntTest extends AbstractManagementServiceIntTest {
    private static final String ADDRESS = "ss1.example.org";
    private static final String SYSTEM_DID = "did:web:ss1.example.org%3A7183:v1:system";
    private static final String DSP_BASE_URL = "https://ss1.example.org:8183/api/dsp";
    private static final KeyPair AUTH_KEYS = TestCertUtil.getKeyPairGenerator().generateKeyPair();
    private static final KeyPair OWNER_KEYS = TestCertUtil.getKeyPairGenerator().generateKeyPair();

    @ParameterizedTest
    @MethodSource("directRequestTypes")
    void directRequestIsRefusedWithoutAdminApiCall(ManagementRequestType type, RequestFactory requestFactory)
            throws Exception {
        var response = executeRequest(signedRequest(requestFactory).createPayload());

        assertSoapFaultCodeAndString(response, 500, "invalid_request", "Unsupported request type " + type);
        assertAdminApiReceivedNoRequest();
    }

    private static Stream<Arguments> directRequestTypes() {
        RequestFactory authCertReg = (builder, server, authCert) -> builder.buildAuthCertRegRequest(server, ADDRESS, authCert);
        RequestFactory systemDidReg = (builder, server, authCert) ->
                builder.buildSystemDidRegRequest(server, SYSTEM_DID, DSP_BASE_URL);
        return Stream.of(
                Arguments.of(ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST, Named.of("authCertReg", authCertReg)),
                Arguments.of(ManagementRequestType.SYSTEM_DID_REGISTRATION_REQUEST, Named.of("systemDidReg", systemDidReg)));
    }

    private static TestAuthRegTypeRequest signedRequest(RequestFactory requestFactory) throws Exception {
        var authCert = TestCertUtil.generateAuthCert(AUTH_KEYS.getPublic());
        var ownerCert = TestCertUtil.generateSignCert(OWNER_KEYS.getPublic(), DEFAULT_SERVER_ID.getOwner());
        var ownerOcsp = OcspTestUtils.createOCSPResponse(ownerCert,
                TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key,
                CertificateStatus.GOOD);

        var builder = new TestManagementRequestBuilder(DEFAULT_SERVER_ID.getOwner(), DEFAULT_RECEIVER);
        var request = requestFactory.build(builder, DEFAULT_SERVER_ID, authCert);

        return new TestAuthRegTypeRequest(authCert, ownerCert.getEncoded(), ownerOcsp.getEncoded(), request,
                AUTH_KEYS.getPrivate(), OWNER_KEYS.getPrivate());
    }

    private interface RequestFactory {
        SoapMessageImpl build(TestManagementRequestBuilder builder, SecurityServerId.Conf server, byte[] authCert);
    }
}

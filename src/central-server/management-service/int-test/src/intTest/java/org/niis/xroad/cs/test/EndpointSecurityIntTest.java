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
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.niis.xroad.common.managemenetrequest.test.TestGenericClientRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_RECEIVER;
import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_SERVER_ID;

/**
 * 005 - Management requests API: endpoint security. Runs first in the suite - it only probes HTTP methods
 * and paths, with no shared admin-API/database state for later classes to build on.
 */
@Order(5)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class EndpointSecurityIntTest extends AbstractManagementServiceIntTest {

    private static final int REQUEST_SIZE_LIMIT = 100_000;

    @Order(1)
    @ParameterizedTest(name = "{0} is not allowed on the management endpoint")
    @DisplayName("Verify http method not allowed")
    @CsvSource({"GET", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"})
    void httpMethodNotAllowed(String method) {
        var response = sendRawRequest(method, "/managementservice/manage");
        assertSoapFault403(response);
    }

    @Order(2)
    @ParameterizedTest(name = "{0} {1} returns 403")
    @DisplayName("Verify not existing endpoint returns 403")
    @CsvSource({
            "GET,/managementservice/manage-x",
            "POST,/managementservice/manage-x",
            "HEAD,/managementservice/health",
            "OPTIONS,/managementservice/info",
            "GET,/something",
            "POST,/something"
    })
    void notExistingEndpointReturns403(String method, String url) {
        var response = sendRawRequest(method, url);
        assertSoapFault403(response);
    }

    @Order(3)
    @Test
    @DisplayName("Request is limited to 100000 bytes")
    void requestIsLimitedToMaxBytes() throws Exception {
        var longSubsystemName = RandomStringUtils.insecure().nextAlphanumeric(REQUEST_SIZE_LIMIT);
        var clientId = resolveClientIdFromEncodedStr("EE:CLASS:MEMBER");
        var request = TestGenericClientRequestBuilder.newBuilder()
                .withSenderClientId(clientId.getMemberId())
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withServerId(DEFAULT_SERVER_ID)
                .withClientId(clientId)
                .withClientOcsp(CertificateStatus.GOOD)
                .withSoapMessageBuilder((builder, serverId, reqClientId) ->
                        builder.buildClientRegRequest(serverId, reqClientId, longSubsystemName))
                .build();

        var response = executeRequest(request.createPayload());
        assertSoapFaultCodeAndString(response, 500, "bad_request", "Request size limit " + REQUEST_SIZE_LIMIT + " exceeded");
    }

    private void assertSoapFault403(Response response) {
        assertThat(response.statusCode()).isEqualTo(403);
        assertNoOtherHeadersThan(response, "Date, Content-Length");
    }
}

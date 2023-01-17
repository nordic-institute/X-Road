/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.cs.test.glue;

import com.nortal.test.asserts.Assertion;
import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.niis.xroad.common.managemenetrequest.test.TestGenericClientRequest;
import org.niis.xroad.common.managemenetrequest.test.TestGenericClientRequestBuilder;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestBuilder;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestPayload;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_RECEIVER;
import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_SERVER_ID;

@Slf4j
@SuppressWarnings({"SpringJavaAutowiredMembersInspection", "checkstyle:MagicNumber"})
public class ManagementRequestStepDefs extends BaseStepDefs {

    @Step("Response of status code {int} and requestId {int} is returned")
    public void responseIsValidated(Integer statusCode, Integer id) {
        ResponseEntity<String> responseEntity = getRequiredStepData(StepDataKey.RESPONSE);
        validate(responseEntity)
                .assertion(equalsStatusCodeAssertion(HttpStatus.valueOf(statusCode)))
                .assertion(xpath(responseEntity.getBody(),
                        "//xroad:requestId",
                        String.valueOf(id)))
                .execute();
    }

    @Step("Response of status code {int} and soap fault is returned")
    public void responseIsValidated(Integer statusCode) {
        ResponseEntity<String> responseEntity = getRequiredStepData(StepDataKey.RESPONSE);
        validate(responseEntity)
                .assertion(equalsStatusCodeAssertion(HttpStatus.valueOf(statusCode)))
                .assertion(xpathExists(responseEntity.getBody(), "//soap:Fault"))
                .execute();
    }

    @SneakyThrows
    @Step("Response of status code {int} and soap fault {string} is returned")
    public void responseIsValidatedWithFaultCode(Integer statusCode, String code) {
        ResponseEntity<String> responseEntity = getRequiredStepData(StepDataKey.RESPONSE);
        var msg = messageFactory.createMessage(null,
                new ByteArrayInputStream(Objects.requireNonNull(responseEntity.getBody()).getBytes(StandardCharsets.UTF_8)));
        validate(responseEntity)
                .assertion(equalsStatusCodeAssertion(HttpStatus.valueOf(statusCode)))
                .assertion(new Assertion.Builder()
                        .message("Verify fault code")
                        .expression("=")
                        .actualValue(msg.getSOAPBody().getFault().getFaultCode())
                        .expectedValue(code)
                        .build())
                .execute();
    }

    @Step("Client Registration request with ClientId {string} was sent")
    public void executeRequest(String clientIdStr) throws Exception {
        var req = TestGenericClientRequestBuilder.newBuilder()
                .withServerId(DEFAULT_SERVER_ID)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withClientId(resolveClientIdFromEncodedStr(clientIdStr))
                .withClientOcsp(CertificateStatus.GOOD)
                .build();
        executeRequest(req.createPayload());
    }

    @Step("Owner change request with ClientId {string} was sent")
    public void executeRequestOwnerChange(String clientIdStr) throws Exception {
        var req = TestGenericClientRequestBuilder.newBuilder()
                .withServerId(DEFAULT_SERVER_ID)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withClientId(resolveClientIdFromEncodedStr(clientIdStr))
                .withClientOcsp(CertificateStatus.GOOD)
                .withSoapMessageBuilder(TestManagementRequestBuilder::buildOwnerChangeRegRequest)
                .build();
        executeRequest(req.createPayload());
    }

    @Step("Client Registration request with ClientId {string} and serverId {string} and receiverId {string} was sent")
    public void executeRequestWithCustomServerId(String clientIdStr, String serverId, String receiverId) throws Exception {
        var req = TestGenericClientRequestBuilder.newBuilder()
                .withServerId(resolveServerIdFromEncodedStr(serverId))
                .withReceiverClientId(resolveClientIdFromEncodedStr(receiverId))
                .withClientId(resolveClientIdFromEncodedStr(clientIdStr))
                .withClientOcsp(CertificateStatus.GOOD)
                .build();
        executeRequest(req.createPayload());
    }

    @Step("Client Registration request with ClientId {string} and invalid signature was sent")
    public void executeRequestWithInvalidSignature(String clientIdStr) throws Exception {
        var request = TestGenericClientRequestBuilder.newBuilder()
                .withServerId(DEFAULT_SERVER_ID)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withClientId(resolveClientIdFromEncodedStr(clientIdStr))
                .withClientOcsp(CertificateStatus.GOOD)
                .withRequestTypeBuilder((keyPairGenerator, clientCert, clientCertOcsp, clientKey, req) ->
                        new TestGenericClientRequest(
                                clientCert, clientCertOcsp,
                                keyPairGenerator.generateKeyPair().getPrivate(), //Pass wrong keypair
                                req))

                .build();
        executeRequest(request.createPayload());
    }

    @Step("Client Registration request with ClientId {string} and invalid client certificate was sent")
    public void executeRequestWithInvalidCert(String clientIdStr) throws Exception {
        var request = TestGenericClientRequestBuilder.newBuilder()
                .withServerId(DEFAULT_SERVER_ID)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withClientId(resolveClientIdFromEncodedStr(clientIdStr))
                .withClientOcsp(CertificateStatus.GOOD)
                .withRequestTypeBuilder((keyPairGenerator, clientCert, clientCertOcsp, clientKey, req) ->
                        new TestGenericClientRequest(
                                new byte[0],  //Pass wrong cert
                                clientCertOcsp, clientKey, req))

                .build();
        executeRequest(request.createPayload());
    }

    @Step("Client Registration request with ClientId {string} and revoked OCSP was sent")
    public void executeRequestWithRevokedOcsp(String clientIdStr) throws Exception {
        var request = TestGenericClientRequestBuilder.newBuilder()
                .withServerId(DEFAULT_SERVER_ID)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withClientId(resolveClientIdFromEncodedStr(clientIdStr))
                .withClientOcsp(new RevokedStatus(Date.from(Instant.now().minusSeconds(3600)), CRLReason.unspecified))
                .build();
        executeRequest(request.createPayload());
    }

    @Step("Client Registration request with empty request was sent")
    public void executeRequestWithEmptyRequest() {
        executeRequest(TestManagementRequestPayload.empty());
    }


}

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
package org.niis.xroad.cs.test.glue;

import ee.ria.xroad.common.TestCertUtil;

import io.cucumber.java.en.Step;
import org.niis.xroad.common.managemenetrequest.test.TestSimpleManagementRequestBuilder;

import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_RECEIVER;
import static org.niis.xroad.cs.test.constants.CommonTestData.DEFAULT_SERVER_ID;

@SuppressWarnings({"SpringJavaAutowiredMembersInspection", "checkstyle:MagicNumber"})
public class DeletionManagementRequestStepDefs extends BaseStepDefs {

    @Step("Client deletion request with clientId {string} was sent")
    public void executeClientDeletionRequest(String clientIdStr) {
        executeClientDeletionRequestWithCustomServerId(clientIdStr, DEFAULT_SERVER_ID.asEncodedId());
    }

    @Step("Client deletion request with clientId {string} and serverId {string} was sent")
    public void executeClientDeletionRequestWithCustomServerId(String clientIdStr, String serverIdStr) {
        var clientId = resolveClientIdFromEncodedStr(clientIdStr);
        var securityServerId = resolveServerIdFromEncodedStr(serverIdStr);
        var request = TestSimpleManagementRequestBuilder.newBuilder()
                .withSenderClientId(clientId)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withSoapMessageBuilder((keyPairGenerator, builder) -> builder.buildClientDeletionRequest(securityServerId, clientId))
                .build();
        executeRequest(request.createPayload());
    }

    @Step("Auth cert deletion request with clientId {string} was sent")
    public void executeAuthCertDeletionRequest(String clientIdStr) {
        executeAuthCertDeletionRequestWithCustomServerId(clientIdStr, DEFAULT_SERVER_ID.asEncodedId());
    }

    @Step("Auth cert deletion request with clientId {string} and serverId {string} was sent")
    public void executeAuthCertDeletionRequestWithCustomServerId(String clientIdStr, String serverIdStr) {
        var clientId = resolveClientIdFromEncodedStr(clientIdStr);
        var securityServerId = resolveServerIdFromEncodedStr(serverIdStr);
        var request = TestSimpleManagementRequestBuilder.newBuilder()
                .withSenderClientId(clientId)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .withSoapMessageBuilder((keyPairGenerator, builder) -> {
                    var authCert = TestCertUtil.generateAuthCert(keyPairGenerator.generateKeyPair().getPublic());
                    return builder.buildAuthCertDeletionRequest(securityServerId, authCert);
                })
                .build();
        executeRequest(request.createPayload());
    }
}

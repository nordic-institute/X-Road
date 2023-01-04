/**
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

    @Step("Client Deletion request with ClientId {string} was sent")
    public void executeClientDeletionRequest(String clientIdStr) {
        var clientId = resolveClientIdFromEncodedStr(clientIdStr);
        var request = TestSimpleManagementRequestBuilder.newBuilder()
                .withSoapMessageBuilder((keyPairGenerator, builder) -> builder.buildClientDeletionRequest(DEFAULT_SERVER_ID, clientId))
                .withServerId(DEFAULT_SERVER_ID)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .build();
        executeRequest(request.createPayload());
    }

    @Step("Auth cert Deletion request was sent")
    public void executeAuthCertDeletionRequest() {
        var request = TestSimpleManagementRequestBuilder.newBuilder()
                .withSoapMessageBuilder((keyPairGenerator, builder) -> {
                    var authCert = TestCertUtil.generateAuthCert(keyPairGenerator.generateKeyPair().getPublic());
                    return builder.buildAuthCertDeletionRequest(DEFAULT_SERVER_ID, authCert);
                })
                .withServerId(DEFAULT_SERVER_ID)
                .withReceiverClientId(DEFAULT_RECEIVER)
                .build();
        executeRequest(request.createPayload());
    }
}

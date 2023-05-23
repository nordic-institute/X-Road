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
package org.niis.xroad.common.managemenetrequest.test;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapMessageImpl;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.KeyPairGenerator;

import static ee.ria.xroad.common.TestCertUtil.getKeyPairGenerator;

@Slf4j
@SuppressWarnings("checkstyle:HiddenField")
public class TestSimpleManagementRequestBuilder {
    private static final KeyPairGenerator KEY_PAIR_GENERATOR = getKeyPairGenerator();

    private ClientId.Conf receiverClientId;
    private ClientId.Conf senderClientId;

    private SoapMessageBuilder soapMessageBuilder;

    public TestSimpleManagementRequestBuilder withReceiverClientId(ClientId.Conf receiverClientId) {
        this.receiverClientId = receiverClientId;
        return this;
    }

    public TestSimpleManagementRequestBuilder withSoapMessageBuilder(SoapMessageBuilder soapMessageBuilder) {
        this.soapMessageBuilder = soapMessageBuilder;
        return this;
    }

    public TestSimpleManagementRequestBuilder withSenderClientId(ClientId.Conf senderClientId) {
        this.senderClientId = senderClientId;
        return this;
    }

    @SneakyThrows
    public TestSimpleManagementRequest build() {
        var builder = new TestManagementRequestBuilder(senderClientId, receiverClientId);

        return new TestSimpleManagementRequest(soapMessageBuilder.build(KEY_PAIR_GENERATOR, builder));
    }

    public interface SoapMessageBuilder {
        SoapMessageImpl build(KeyPairGenerator keyPairGenerator, TestManagementRequestBuilder builder)
                throws IOException, OperatorCreationException;
    }

    public static TestSimpleManagementRequestBuilder newBuilder() {
        return new TestSimpleManagementRequestBuilder();
    }

}

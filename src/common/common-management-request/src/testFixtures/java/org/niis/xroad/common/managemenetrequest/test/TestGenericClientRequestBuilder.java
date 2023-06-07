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

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessageImpl;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.CertificateStatus;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;

import static ee.ria.xroad.common.TestCertUtil.getKeyPairGenerator;

@Slf4j
@SuppressWarnings("checkstyle:HiddenField")
public class TestGenericClientRequestBuilder {
    private static final KeyPairGenerator KEY_PAIR_GENERATOR = getKeyPairGenerator();

    private ClientId.Conf senderClientId;
    private ClientId.Conf receiverClientId;

    private SecurityServerId.Conf serverId;
    private ClientId.Conf clientId;
    private CertificateStatus clientOcspStatus;

    private TestBaseManagementRequestBuilder requestBuilder = (_keyPairGenerator, _clientCert, _clientCertOcsp, _clientKey, _request) ->
            new TestGenericClientRequest(_clientCert, _clientCertOcsp, _clientKey, _request);

    private RequestAssembler requestAssembler = (_keyPairGenerator, _clientRegTypeBuilder, _soapMessageBuilder, _senderClientId,
                                                 _receiverClientId, _serverId, _clientId, _clientOcspStatus) -> {
        var builder = new TestManagementRequestBuilder(_senderClientId, _receiverClientId);

        var clientKeyPair = _keyPairGenerator.generateKeyPair();
        var clientCert = TestCertUtil.generateSignCert(clientKeyPair.getPublic(), _serverId.getOwner());

        var clientOcsp = OcspTestUtils.createOCSPResponse(clientCert,
                TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key,
                _clientOcspStatus);

        return _clientRegTypeBuilder.build(
                _keyPairGenerator,
                clientCert.getEncoded(),
                clientOcsp.getEncoded(),
                clientKeyPair.getPrivate(),
                _soapMessageBuilder.build(builder, _serverId, _clientId));
    };

    private SoapMessageBuilder soapMessageBuilder = TestManagementRequestBuilder::buildClientRegRequest;

    public TestGenericClientRequestBuilder withServerId(SecurityServerId.Conf serverId) {
        this.serverId = serverId;
        return this;
    }

    public TestGenericClientRequestBuilder withSenderClientId(ClientId.Conf senderClientId) {
        this.senderClientId = senderClientId;
        return this;
    }

    public TestGenericClientRequestBuilder withReceiverClientId(ClientId.Conf receiverClientId) {
        this.receiverClientId = receiverClientId;
        return this;
    }

    public TestGenericClientRequestBuilder withClientId(ClientId.Conf clientId) {
        this.clientId = clientId;
        return this;
    }

    public TestGenericClientRequestBuilder withClientOcsp(CertificateStatus clientOcspStatus) {
        this.clientOcspStatus = clientOcspStatus;
        return this;
    }

    public TestGenericClientRequestBuilder withRequestAssembler(RequestAssembler requestAssembler) {
        this.requestAssembler = requestAssembler;
        return this;
    }

    public TestGenericClientRequestBuilder withSoapMessageBuilder(SoapMessageBuilder soapMessageBuilder) {
        this.soapMessageBuilder = soapMessageBuilder;
        return this;
    }

    public TestGenericClientRequestBuilder withRequestTypeBuilder(TestBaseManagementRequestBuilder clientRegTypeBuilder) {
        this.requestBuilder = clientRegTypeBuilder;
        return this;
    }

    @SneakyThrows
    public TestGenericClientRequest build() {
        return requestAssembler.assemble(KEY_PAIR_GENERATOR, requestBuilder, soapMessageBuilder,
                senderClientId, receiverClientId, serverId, clientId, clientOcspStatus);
    }

    public interface RequestAssembler {
        TestGenericClientRequest assemble(KeyPairGenerator keyPairGenerator,
                                          TestBaseManagementRequestBuilder clientRegTypeBuilder,
                                          SoapMessageBuilder soapMessageBuilder,
                                          ClientId.Conf senderClientId, ClientId.Conf receiverClientId,
                                          SecurityServerId.Conf serverId, ClientId.Conf clientId,
                                          CertificateStatus clientOcspStatus) throws IOException, CertificateEncodingException;
    }

    public interface TestBaseManagementRequestBuilder {
        TestGenericClientRequest build(KeyPairGenerator keyPairGenerator, byte[] clientCert, byte[] clientCertOcsp,
                                       PrivateKey clientKey, SoapMessageImpl request);
    }

    public interface SoapMessageBuilder {
        SoapMessageImpl build(TestManagementRequestBuilder builder, SecurityServerId.Conf serverId, ClientId.Conf clientId);
    }

    public static TestGenericClientRequestBuilder newBuilder() {
        return new TestGenericClientRequestBuilder();
    }

}

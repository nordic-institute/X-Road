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

package org.niis.xroad.signer.test;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.signer.client.impl.SignerSignRpcClient;
import org.niis.xroad.signer.test.container.SignerIntTestSetup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static ee.ria.xroad.common.PortNumbers.SIGNER_GRPC_PORT;

/**
 * Holder for SignerRpcClient instance. Holds the signerRpcClient instance that is used in the tests. Otherwise, would
 * need to recreate on every feature.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "test-automation.custom.signer-container-enabled", havingValue = "true")
@RequiredArgsConstructor
public class SignerClientHolder {
    private final SignerIntTestSetup signerIntTestSetup;

    private SignerRpcClient signerRpcClientInstance;
    private SignerSignRpcClient signerSignClient;

    public SignerRpcClient get() {
        return signerRpcClientInstance;
    }

    public SignerSignClient getSignClient() {
        return signerSignClient;
    }

    @SneakyThrows
    public SignerRpcClient initWithTimeout(int timeoutMillis) {
        var properties = new SignerRpcChannelProperties() {
            @Override
            public String host() {
                return signerIntTestSetup.getContainerMapping(SignerIntTestSetup.SIGNER, SIGNER_GRPC_PORT).host();
            }

            @Override
            public int port() {
                return signerIntTestSetup.getContainerMapping(SignerIntTestSetup.SIGNER, SIGNER_GRPC_PORT).port();

            }

            @Override
            public int deadlineAfter() {
                return timeoutMillis;
            }
        };

        signerRpcClientInstance = new SignerRpcClient(getFactory(), properties);
        signerRpcClientInstance.init();

        signerSignClient = new SignerSignRpcClient(getFactory(), properties);
        signerSignClient.init();

        log.info("Will use {}:{} (original port {})  for signer RPC connection..", properties.host(), properties.port(), SIGNER_GRPC_PORT);
        return signerRpcClientInstance;
    }

    private RpcChannelFactory getFactory() {
        return new RpcChannelFactory(new InsecureRpcCredentialsConfigurer());
    }
}

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

package org.niis.xroad.softtoken.signer.test;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.impl.SignerSignRpcClient;
import org.springframework.stereotype.Component;

import static org.niis.xroad.softtoken.signer.test.SoftTokenSignerIntTestContainerSetup.Port.SOFTTOKEN_SIGNER_GRPC;
import static org.niis.xroad.softtoken.signer.test.SoftTokenSignerIntTestContainerSetup.SIGNER;
import static org.niis.xroad.softtoken.signer.test.SoftTokenSignerIntTestContainerSetup.SOFTTOKEN_SIGNER;

/**
 * Holder for SignerRpcClient (for signer) and SignerSignRpcClient (for softtoken-signer) instances. Otherwise, would
 * need to recreate on every feature.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftTokenSignerClientHolder {
    private static final int DEFAULT_TIMEOUT_MILLIS = 60000;
    private static final int SIGNER_GRPC_PORT = Integer.parseInt(SignerRpcChannelProperties.DEFAULT_PORT);
    private final SoftTokenSignerIntTestContainerSetup containerSetup;

    @Getter
    private SignerRpcClient signerClient;

    @Getter
    private SignerSignRpcClient softTokenSignerSignClient;

    /**
     * Initialize gRPC clients after container setup is ready.
     */
    @PostConstruct
    @SneakyThrows
    public void init() {
        log.info("Initializing signer and softtoken-signer gRPC clients");

        // Initialize signer client
        var signerMapping = containerSetup.getContainerMapping(SIGNER, SIGNER_GRPC_PORT);
        var signerProperties = createChannelProperties(signerMapping.host(), signerMapping.port());

        signerClient = new SignerRpcClient(getFactory(), signerProperties);
        signerClient.init();

        log.info("Initialized signer client: {}:{}", signerMapping.host(), signerMapping.port());

        // Initialize softtoken-signer client
        var softtokenMapping = containerSetup.getContainerMapping(SOFTTOKEN_SIGNER, SOFTTOKEN_SIGNER_GRPC);
        var softtokenProperties = createChannelProperties(softtokenMapping.host(), softtokenMapping.port());

        softTokenSignerSignClient = new SignerSignRpcClient(getFactory(), softtokenProperties);

        log.info("Initialized softtoken-signer client: {}:{}", softtokenMapping.host(), softtokenMapping.port());
    }

    private SignerRpcChannelProperties createChannelProperties(String host, int port) {
        return new SignerRpcChannelProperties() {
            @Override
            public String host() {
                return host;
            }

            @Override
            public int port() {
                return port;
            }

            @Override
            public int deadlineAfter() {
                return DEFAULT_TIMEOUT_MILLIS;
            }
        };
    }

    private RpcChannelFactory getFactory() {
        return new RpcChannelFactory(new InsecureRpcCredentialsConfigurer());
    }
}

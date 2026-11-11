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
package org.niis.xroad.common.rpc;

import io.grpc.CallOptions;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.credentials.ChannelTrust;
import org.niis.xroad.common.rpc.credentials.TlsRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.common.rpc.test.TestVaultKeyProvider;
import org.niis.xroad.common.rpc.test.TlsTestServer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelTrustHandshakeTest {
    private static final String HOST = "localhost";
    private static final String OTHER_HOST = "other.example.org";
    private static final byte[] PAYLOAD = {1, 2, 3};
    private static final long DEADLINE_SECONDS = 10;

    private TestCa securityServerCa;
    private TestCa singleCa;
    private TlsRpcCredentialsConfigurer configurer;
    private TlsTestServer server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        securityServerCa = TestCa.selfSigned("Security Server CA");
        singleCa = TestCa.selfSigned("Single CA");
        var clientIdentity = securityServerCa.issueLeaf("proxy", List.of(HOST), List.of());
        configurer = new TlsRpcCredentialsConfigurer(new TestVaultKeyProvider(clientIdentity, securityServerCa.certificate()));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(DEADLINE_SECONDS, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void onlyCaTrustsTheServerCertificateOfThatCaAndPresentsTheSharedClientIdentity() throws Exception {
        startServer(singleCa, HOST);

        connect(ChannelTrust.onlyCa(singleCa.certificate()));

        assertThat(echo()).isEqualTo(PAYLOAD);
    }

    @Test
    void onlyCaRejectsTheServerCertificateOfTheSecurityServerCa() throws Exception {
        startServer(securityServerCa, HOST);

        connect(ChannelTrust.onlyCa(singleCa.certificate()));

        assertRejected();
    }

    @Test
    void onlyCaRejectsAServerCertificateWhoseNameDiffersFromTheDialedHost() throws Exception {
        startServer(singleCa, OTHER_HOST);

        connect(ChannelTrust.onlyCa(singleCa.certificate()));

        assertRejected();
    }

    @Test
    void sharedTrustsTheSecurityServerCa() throws Exception {
        startServer(securityServerCa, HOST);

        connect(ChannelTrust.shared());

        assertThat(echo()).isEqualTo(PAYLOAD);
    }

    @Test
    void sharedRejectsTheServerCertificateOfAnotherCa() throws Exception {
        startServer(singleCa, HOST);

        connect(ChannelTrust.shared());

        assertRejected();
    }

    private void startServer(TestCa issuer, String dnsName) throws Exception {
        var serverIdentity = issuer.issueLeaf("op-monitor", List.of(dnsName), List.of());
        server = TlsTestServer.start(serverIdentity, securityServerCa.certificate(), new ByteEchoService());
    }

    private void connect(ChannelTrust trust) {
        channel = Grpc.newChannelBuilderForAddress(HOST, server.port(), configurer.createClientCredentials(trust)).build();
    }

    private void assertRejected() {
        assertThatThrownBy(this::echo)
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
    }

    private byte[] echo() {
        return ClientCalls.blockingUnaryCall(channel, ByteEchoService.ECHO,
                CallOptions.DEFAULT.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS), PAYLOAD);
    }
}

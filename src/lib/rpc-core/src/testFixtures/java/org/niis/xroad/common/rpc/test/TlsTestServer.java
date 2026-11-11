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
package org.niis.xroad.common.rpc.test;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.TlsServerCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * A real Netty gRPC server doing mutual TLS on a free port: it presents the given identity and requires a client
 * certificate that chains to one of the given trust anchors.
 */
public final class TlsTestServer implements AutoCloseable {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final Server server;

    private TlsTestServer(Server server) {
        this.server = server;
    }

    public static TlsTestServer start(TestCa.TestLeaf identity, X509Certificate clientTrustAnchor, BindableService... services)
            throws Exception {
        var keyManager = new AdvancedTlsX509KeyManager();
        keyManager.updateIdentityCredentials(identity.chain(), identity.privateKey());
        var trustManager = AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                .build();
        trustManager.updateTrustCredentials(new X509Certificate[] {clientTrustAnchor});

        var credentials = TlsServerCredentials.newBuilder()
                .keyManager(keyManager)
                .trustManager(trustManager)
                .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE)
                .build();
        var builder = NettyServerBuilder.forPort(0, credentials);
        for (var service : services) {
            builder.addService(service);
        }
        try {
            return new TlsTestServer(builder.build().start());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start the TLS test server", e);
        }
    }

    public int port() {
        return server.getPort();
    }

    @Override
    public void close() throws InterruptedException {
        server.shutdownNow().awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}

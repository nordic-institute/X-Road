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

import io.grpc.ConnectivityState;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import org.niis.xroad.common.rpc.credentials.TlsRpcCredentialsConfigurer;

import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Client-side helpers for tests that run against a {@link TlsTestServer}.
 */
public final class TlsTestChannels {

    private TlsTestChannels() {
    }

    /**
     * Opens a mutual-TLS channel that presents the given identity and trusts only the given server trust anchor. The
     * server name is verified against {@code authority}, which may differ from the dialed host.
     */
    public static ManagedChannel trustedChannel(String host, int port, String authority, TestCa.TestLeaf clientIdentity,
                                                X509Certificate serverTrustAnchor) throws Exception {
        var keyProvider = new TestVaultKeyProvider(clientIdentity, serverTrustAnchor);
        var credentials = new TlsRpcCredentialsConfigurer(keyProvider).createClientCredentials();
        return Grpc.newChannelBuilderForAddress(host, port, credentials)
                .overrideAuthority(authority)
                .build();
    }

    /**
     * Waits, by state-change notification, until the channel is neither {@code IDLE} nor {@code CONNECTING} or the
     * timeout elapses, and returns the state then observed.
     */
    public static ConnectivityState awaitSettledState(ManagedChannel channel, long timeoutSeconds) throws InterruptedException {
        var deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        var state = channel.getState(false);
        while (isTransitional(state)) {
            var remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            var changed = new CountDownLatch(1);
            channel.notifyWhenStateChanged(state, changed::countDown);
            if (!changed.await(remainingNanos, TimeUnit.NANOSECONDS)) {
                break;
            }
            state = channel.getState(false);
        }
        return state;
    }

    private static boolean isTransitional(ConnectivityState state) {
        return state == ConnectivityState.IDLE || state == ConnectivityState.CONNECTING;
    }
}

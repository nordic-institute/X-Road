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
package org.niis.xroad.edc.trust;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * The trust manager EDC's singleton OkHttp client is built with: a static routing structure — built once and
 * never replaced — around a {@link DelegatingTrustManager} carrying the reloadable, DS-TLS-CA-list-derived
 * trust. When present, the vault trust exception is consulted exclusively for traffic to its exact host and
 * port; every other target, including a host that happens to present a certificate chaining to the vault's own
 * CA, is judged by the DS TLS CA list alone. Whenever the peer's host and port cannot be determined — no socket
 * on the no-socket {@code checkServerTrusted} overload, a plain (non-TLS) socket, or a socket whose handshake
 * session does not yet expose a peer — the vault exception is skipped and the list-derived trust decides, so an
 * indeterminate peer can never accidentally land in the narrower vault-only path.
 */
public final class DsTlsCompositeTrustManager extends X509ExtendedTrustManager {

    private final VaultEndpointTrust vaultTrust;
    private final X509ExtendedTrustManager listTrustManager;

    public DsTlsCompositeTrustManager(VaultEndpointTrust vaultTrust, X509ExtendedTrustManager listTrustManager) {
        this.vaultTrust = vaultTrust;
        this.listTrustManager = Objects.requireNonNull(listTrustManager);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        listTrustManager.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        listTrustManager.checkClientTrusted(chain, authType, socket);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        listTrustManager.checkClientTrusted(chain, authType, engine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // No socket or engine is available on this overload, so the peer's host and port are indeterminate:
        // the vault exception (host-and-port scoped by design) can never apply here.
        listTrustManager.checkServerTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        if (vaultTrust != null && socket instanceof SSLSocket sslSocket) {
            var session = sslSocket.getHandshakeSession();
            var peerHost = session != null ? session.getPeerHost() : null;
            var peerPort = session != null ? session.getPeerPort() : -1;
            if (peerHost != null && vaultTrust.matchesVaultEndpoint(peerHost, peerPort)) {
                vaultTrust.checkServerTrusted(chain, authType);
                return;
            }
        }
        listTrustManager.checkServerTrusted(chain, authType, socket);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        if (vaultTrust != null && engine != null) {
            var peerHost = engine.getPeerHost();
            var peerPort = engine.getPeerPort();
            if (peerHost != null && vaultTrust.matchesVaultEndpoint(peerHost, peerPort)) {
                vaultTrust.checkServerTrusted(chain, authType);
                return;
            }
        }
        listTrustManager.checkServerTrusted(chain, authType, engine);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        if (vaultTrust == null) {
            return listTrustManager.getAcceptedIssuers();
        }
        var combined = new ArrayList<X509Certificate>();
        combined.addAll(Arrays.asList(vaultTrust.getAcceptedIssuers()));
        combined.addAll(Arrays.asList(listTrustManager.getAcceptedIssuers()));
        return combined.toArray(new X509Certificate[0]);
    }
}

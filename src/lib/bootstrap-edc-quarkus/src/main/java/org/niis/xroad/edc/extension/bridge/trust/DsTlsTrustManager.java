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
package org.niis.xroad.edc.extension.bridge.trust;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;

import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * The one stable {@link X509ExtendedTrustManager} instance wired into EDC's shared
 * {@code OkHttpClient} for its whole lifetime - {@link #update} swaps its trust anchors in place so
 * the dataspace TLS trust set can hot-reload without rebuilding the client or its
 * {@link javax.net.ssl.SSLContext}.
 *
 * <p>Trusts exactly the {@code approvedDsTlsCa} chains most recently applied via {@link #update} -
 * the JVM default trust store is never consulted, and neither is the member {@code approvedCA}
 * list. An empty trust set rejects every connection (see {@link RejectAllTrustManager}).
 *
 * <p>One host-scoped exception: when the peer being dialed is EDC's own OpenBao (Hashicorp Vault)
 * endpoint - determined from the real {@link Socket} or {@link SSLEngine} handshake session, never
 * from certificate content - the configured vault CA certificate(s) are additionally trusted. Every
 * path where the peer host cannot be determined (the plain two-argument {@code X509TrustManager}
 * overload, a non-SSL socket, a session with no resolvable peer port, or simply no vault
 * endpoint/certificate configured) falls back to the DS TLS CA list alone; there is no host-only
 * matching mode.
 */
public final class DsTlsTrustManager extends X509ExtendedTrustManager {

    private final VaultEndpoint vaultEndpoint;
    private final List<X509Certificate> vaultCaCertificates;

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public DsTlsTrustManager(VaultEndpoint vaultEndpoint, List<X509Certificate> vaultCaCertificates) {
        this.vaultEndpoint = vaultEndpoint;
        this.vaultCaCertificates = List.copyOf(vaultCaCertificates);
    }

    /** Rebuilds and atomically swaps the trust anchors in place; safe to call during live handshakes. */
    public void update(List<X509Certificate> approvedDsTlsCas) throws IOException, GeneralSecurityException {
        var listOnly = TrustManagers.fromTrustAnchors(approvedDsTlsCas);
        var listPlusVault = listOnly;
        if (!vaultCaCertificates.isEmpty()) {
            var combined = new ArrayList<>(approvedDsTlsCas);
            combined.addAll(vaultCaCertificates);
            listPlusVault = TrustManagers.fromTrustAnchors(combined);
        }
        snapshot = new Snapshot(listOnly, listPlusVault);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        select(false).checkClientTrusted(chain, authType);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        select(false).checkClientTrusted(chain, authType, socket);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        select(false).checkClientTrusted(chain, authType, engine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // No socket/engine available at all: the peer host is indeterminate, so the vault-scoped
        // exception never applies here, regardless of what is actually being dialed.
        select(false).checkServerTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        select(isVaultEndpoint(socket)).checkServerTrusted(chain, authType, socket);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        select(isVaultEndpoint(engine)).checkServerTrusted(chain, authType, engine);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return select(true).getAcceptedIssuers();
    }

    private X509ExtendedTrustManager select(boolean vaultScoped) {
        var current = snapshot;
        return vaultScoped ? current.listPlusVault() : current.listOnly();
    }

    private boolean isVaultEndpoint(Socket socket) {
        if (vaultEndpoint == null || vaultCaCertificates.isEmpty() || !(socket instanceof SSLSocket sslSocket)) {
            return false;
        }
        return isVaultEndpoint(handshakeSession(sslSocket));
    }

    private boolean isVaultEndpoint(SSLEngine engine) {
        if (vaultEndpoint == null || vaultCaCertificates.isEmpty() || engine == null) {
            return false;
        }
        return isVaultEndpoint(handshakeSession(engine));
    }

    private boolean isVaultEndpoint(SSLSession session) {
        if (session == null) {
            return false;
        }
        var peerHost = session.getPeerHost();
        var peerPort = session.getPeerPort();
        return peerPort > 0 && peerPort == vaultEndpoint.port() && peerHost != null && peerHost.equalsIgnoreCase(vaultEndpoint.host());
    }

    private static SSLSession handshakeSession(SSLSocket socket) {
        var session = socket.getHandshakeSession();
        return session != null ? session : socket.getSession();
    }

    private static SSLSession handshakeSession(SSLEngine engine) {
        var session = engine.getHandshakeSession();
        return session != null ? session : engine.getSession();
    }

    private record Snapshot(X509ExtendedTrustManager listOnly, X509ExtendedTrustManager listPlusVault) {
        static final Snapshot EMPTY = new Snapshot(RejectAllTrustManager.INSTANCE, RejectAllTrustManager.INSTANCE);
    }
}

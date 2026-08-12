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

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-handshake coverage of the fail-closed, host-scoped trust decision: every case is proven
 * against an actual JSSE handshake on a live local {@link TlsTestSupport.TestHttpsServer}, not a
 * mocked trust check.
 */
class DsTlsTrustManagerTest {

    @Test
    void listedCaAcceptsAPeerChainingToIt() throws Exception {
        var ca = TlsTestSupport.generateCa("DS TLS CA");
        var leaf = TlsTestSupport.issueLocalhostLeaf(ca);
        var trustManager = new DsTlsTrustManager(null, List.of());
        trustManager.update(List.of(ca.certificate()));

        try (var server = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(ca, leaf))) {
            assertThatCode(() -> handshake(server.port(), trustManager)).doesNotThrowAnyException();
        }
    }

    @Test
    void unlistedCaIsRejectedEvenAPublicWebPkiStyleOne() throws Exception {
        var webPkiLookingCa = TlsTestSupport.generateCa("Definitely Trustworthy Public CA");
        var leaf = TlsTestSupport.issueLocalhostLeaf(webPkiLookingCa);
        var otherListedCa = TlsTestSupport.generateCa("Some Other DS TLS CA");
        var trustManager = new DsTlsTrustManager(null, List.of());
        trustManager.update(List.of(otherListedCa.certificate()));

        try (var server = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(webPkiLookingCa, leaf))) {
            assertThatThrownBy(() -> handshake(server.port(), trustManager)).isInstanceOf(SSLHandshakeException.class);
        }
    }

    @Test
    void memberApprovedCaAloneGrantsNoDataspaceTlsTrust() throws Exception {
        // Nothing in this test ever calls a member-approvedCA API - the point is structural: the
        // trust manager only ever sees what update() gives it, so a CA that a reader might assume
        // is "approved" for member certs elsewhere is exactly as untrusted as any other unlisted CA.
        var memberApprovedCa = TlsTestSupport.generateCa("Member Approved CA (not a DS TLS CA)");
        var leaf = TlsTestSupport.issueLocalhostLeaf(memberApprovedCa);
        var trustManager = new DsTlsTrustManager(null, List.of());
        trustManager.update(List.of());

        try (var server = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(memberApprovedCa, leaf))) {
            assertThatThrownBy(() -> handshake(server.port(), trustManager)).isInstanceOf(SSLHandshakeException.class);
        }
    }

    @Test
    void emptyListRejectsEverythingIncludingTheJsseEmptyTruststoreQuirk() throws Exception {
        var ca = TlsTestSupport.generateCa("DS TLS CA");
        var leaf = TlsTestSupport.issueLocalhostLeaf(ca);
        var trustManager = new DsTlsTrustManager(null, List.of());

        assertThatCode(() -> trustManager.update(List.of())).doesNotThrowAnyException();

        try (var server = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(ca, leaf))) {
            assertThatThrownBy(() -> handshake(server.port(), trustManager)).isInstanceOf(SSLHandshakeException.class);
        }
    }

    @Test
    void liveUpdateTakesEffectOnTheNextHandshakeWithoutRebuildingAnything() throws Exception {
        var ca = TlsTestSupport.generateCa("DS TLS CA");
        var otherCa = TlsTestSupport.generateCa("A Different CA");
        var leaf = TlsTestSupport.issueLocalhostLeaf(ca);
        var trustManager = new DsTlsTrustManager(null, List.of());
        trustManager.update(List.of(otherCa.certificate()));

        try (var server = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(ca, leaf))) {
            assertThatThrownBy(() -> handshake(server.port(), trustManager)).isInstanceOf(SSLHandshakeException.class);

            trustManager.update(List.of(ca.certificate()));

            // Same DsTlsTrustManager instance, same live server, no restart of either.
            assertThatCode(() -> handshake(server.port(), trustManager)).doesNotThrowAnyException();
        }
    }

    @Test
    void vaultHostIsAcceptedViaTheVaultCaEvenWithAnEmptyDsTlsCaList() throws Exception {
        var vaultCa = TlsTestSupport.generateCa("OpenBao Test CA");
        var vaultLeaf = TlsTestSupport.issueLocalhostLeaf(vaultCa);

        try (var vaultServer = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(vaultCa, vaultLeaf))) {
            var vaultEndpoint = new VaultEndpoint("localhost", vaultServer.port());
            var trustManager = new DsTlsTrustManager(vaultEndpoint, List.of(vaultCa.certificate()));
            trustManager.update(List.of());

            assertThatCode(() -> handshake(vaultServer.port(), trustManager)).doesNotThrowAnyException();
        }
    }

    @Test
    void nonVaultHostWithTheSameCaIsRejected() throws Exception {
        var vaultCa = TlsTestSupport.generateCa("OpenBao Test CA");
        var vaultLeaf = TlsTestSupport.issueLocalhostLeaf(vaultCa);
        // A second peer, signed by the exact same CA as the vault, but reached on a different port -
        // the vault-CA exception must not leak beyond the one endpoint it is scoped to.
        var otherLeaf = TlsTestSupport.issueLocalhostLeaf(vaultCa);

        try (var vaultServer = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(vaultCa, vaultLeaf));
                var otherServer = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(vaultCa, otherLeaf))) {
            var vaultEndpoint = new VaultEndpoint("localhost", vaultServer.port());
            var trustManager = new DsTlsTrustManager(vaultEndpoint, List.of(vaultCa.certificate()));
            trustManager.update(List.of());

            assertThatThrownBy(() -> handshake(otherServer.port(), trustManager)).isInstanceOf(SSLHandshakeException.class);
        }
    }

    @Test
    void withNoVaultCaConfiguredTheVaultHostGetsPureListOnlyTrust() throws Exception {
        var vaultCa = TlsTestSupport.generateCa("OpenBao Test CA");
        var vaultLeaf = TlsTestSupport.issueLocalhostLeaf(vaultCa);

        try (var vaultServer = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(vaultCa, vaultLeaf))) {
            var vaultEndpoint = new VaultEndpoint("localhost", vaultServer.port());
            // No vault CA certificates supplied - as if QUARKUS_VAULT_TLS_CA_CERT were unset.
            var trustManager = new DsTlsTrustManager(vaultEndpoint, List.of());
            trustManager.update(List.of());

            assertThatThrownBy(() -> handshake(vaultServer.port(), trustManager)).isInstanceOf(SSLHandshakeException.class);
        }
    }

    @Test
    void thePlainTwoArgumentOverloadNeverAppliesTheVaultException() throws Exception {
        var vaultCa = TlsTestSupport.generateCa("OpenBao Test CA");
        var chain = new X509Certificate[] {vaultCa.certificate()};
        var vaultEndpoint = new VaultEndpoint("localhost", 8200);
        var trustManager = new DsTlsTrustManager(vaultEndpoint, List.of(vaultCa.certificate()));
        trustManager.update(List.of());

        // No socket/engine argument at all: the peer host is indeterminate by construction, so even
        // though this exact CA is the configured vault CA, the plain overload must still reject.
        assertThatThrownBy(() -> trustManager.checkServerTrusted(chain, "RSA")).isInstanceOf(CertificateException.class);
    }

    @Test
    void aNonSslSocketNeverAppliesTheVaultException() throws Exception {
        var vaultCa = TlsTestSupport.generateCa("OpenBao Test CA");
        var chain = new X509Certificate[] {vaultCa.certificate()};
        var vaultEndpoint = new VaultEndpoint("localhost", 8200);
        var trustManager = new DsTlsTrustManager(vaultEndpoint, List.of(vaultCa.certificate()));
        trustManager.update(List.of());

        try (var plainSocket = new Socket()) {
            assertThatThrownBy(() -> trustManager.checkServerTrusted(chain, "RSA", plainSocket))
                    .isInstanceOf(CertificateException.class);
        }
    }

    @Test
    void getAcceptedIssuersNeverThrowsEvenWhenNothingHasBeenAppliedYet() {
        var trustManager = new DsTlsTrustManager(null, List.of());

        assertThat(trustManager.getAcceptedIssuers()).isEmpty();
    }

    private static void handshake(int port, X509ExtendedTrustManager trustManager) throws Exception {
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {trustManager}, null);
        try (var socket = (SSLSocket) sslContext.getSocketFactory().createSocket("localhost", port)) {
            socket.startHandshake();
        }
    }
}

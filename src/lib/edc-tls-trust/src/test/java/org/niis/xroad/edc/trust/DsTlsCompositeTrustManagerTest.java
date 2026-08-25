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

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import java.io.FileWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-handshake coverage of the trust manager EDC's singleton OkHttp client is built with — the same class
 * assembled by {@code XRoadTlsOkHttpClientExtension}, exercised here directly against real TLS servers instead
 * of through the full EDC/Quarkus boot path.
 */
@ExtendWith(MockitoExtension.class)
class DsTlsCompositeTrustManagerTest {

    @Mock
    private Monitor monitor;

    @TempDir
    private Path tempDir;

    @Test
    void aPeerChainingToAListedCaValidates() throws Exception {
        var listedCa = TestCa.selfSigned("Listed DS TLS CA");
        var leaf = listedCa.issueLeaf("ds.example");
        var trustManager = compositeOf(listOnly(listedCa));

        try (var server = TestTlsServer.start(leaf)) {
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", server.port(), trustManager)).isTrue();
        }
    }

    @Test
    void aPeerChainingToAnUnlistedCaIsRejected() throws Exception {
        var listedCa = TestCa.selfSigned("Listed DS TLS CA");
        var unlistedCa = TestCa.selfSigned("Unlisted CA");
        var leaf = unlistedCa.issueLeaf("ds.example");
        var trustManager = compositeOf(listOnly(listedCa));

        try (var server = TestTlsServer.start(leaf)) {
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", server.port(), trustManager)).isFalse();
        }
    }

    @Test
    void aPeerChainingToAPublicWebPkiStyleCaNotOnTheListIsRejected() throws Exception {
        var listedCa = TestCa.selfSigned("Listed DS TLS CA");
        // Stands in for a well-known public CA (e.g. Let's Encrypt): a perfectly ordinary, otherwise-trustworthy
        // CA that simply never appears in the DS TLS CA list, and must be trusted no more than any other stranger.
        var publicWebPkiCa = TestCa.selfSigned("Public Web PKI CA");
        var leaf = publicWebPkiCa.issueLeaf("ds.example");
        var trustManager = compositeOf(listOnly(listedCa));

        try (var server = TestTlsServer.start(leaf)) {
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", server.port(), trustManager)).isFalse();
        }
    }

    @Test
    void aPeerChainingOnlyToAMemberApprovedCaIsRejected() throws Exception {
        var listedDsTlsCa = TestCa.selfSigned("Listed DS TLS CA");
        // Represents a CA present in the member approvedCA list, but never entered into approvedDsTlsCa. The DS
        // TLS trust manager must never consult the member CA list, so this chain is rejected exactly like any
        // other unlisted CA.
        var memberApprovedCa = TestCa.selfSigned("Member Approved CA");
        var leaf = memberApprovedCa.issueLeaf("ds.example");
        var trustManager = compositeOf(listOnly(listedDsTlsCa));

        try (var server = TestTlsServer.start(leaf)) {
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", server.port(), trustManager)).isFalse();
        }
    }

    @Test
    void withAnEmptyListNoDataSpaceTlsConnectionValidates() throws Exception {
        var anyCa = TestCa.selfSigned("Any CA");
        var leaf = anyCa.issueLeaf("ds.example");
        var trustManager = compositeOf(RejectAllTrustManager.INSTANCE);

        try (var server = TestTlsServer.start(leaf)) {
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", server.port(), trustManager)).isFalse();
        }
    }

    @Test
    void aDsTlsCaListChangeTakesEffectWithoutRebuildingTheTrustManager() throws Exception {
        var firstCa = TestCa.selfSigned("First DS TLS CA");
        var secondCa = TestCa.selfSigned("Second DS TLS CA");
        var firstLeaf = firstCa.issueLeaf("ds.example");
        var secondLeaf = secondCa.issueLeaf("ds.example");

        var delegating = new DelegatingTrustManager(listOnly(firstCa));
        var trustManager = new DsTlsCompositeTrustManager(null, delegating);

        try (var firstServer = TestTlsServer.start(firstLeaf); var secondServer = TestTlsServer.start(secondLeaf)) {
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", firstServer.port(), trustManager)).isTrue();
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", secondServer.port(), trustManager)).isFalse();

            delegating.setDelegate(listOnly(secondCa));

            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", secondServer.port(), trustManager)).isTrue();
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", firstServer.port(), trustManager)).isFalse();
        }
    }

    @Test
    void aVaultCaConfiguredWithAnEmptyListAcceptsOnlyTheVaultHostAndPort() throws Exception {
        var vaultCa = TestCa.selfSigned("Vault CA");
        var vaultLeaf = vaultCa.issueLeaf("vault");
        // Same CA, but served from a different port: proves the vault exception is scoped to host AND port, not
        // merely to "any peer presenting the vault's CA".
        var vaultCaOnAnotherPortLeaf = vaultCa.issueLeaf("vault-elsewhere");

        try (var vaultServer = TestTlsServer.start(vaultLeaf); var otherServer = TestTlsServer.start(vaultCaOnAnotherPortLeaf)) {
            var vaultTrust = VaultEndpointTrust.from("https://127.0.0.1:" + vaultServer.port(), writeCaCert(vaultCa), monitor)
                    .orElseThrow();
            var trustManager = new DsTlsCompositeTrustManager(vaultTrust, RejectAllTrustManager.INSTANCE);

            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", vaultServer.port(), trustManager)).isTrue();
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", otherServer.port(), trustManager)).isFalse();
        }
    }

    @Test
    void withoutAVaultCaTheCompositeIsPureListOnly() throws Exception {
        var listedCa = TestCa.selfSigned("Listed DS TLS CA");
        var leaf = listedCa.issueLeaf("ds.example");
        var trustManager = new DsTlsCompositeTrustManager(null, listOnly(listedCa));

        try (var server = TestTlsServer.start(leaf)) {
            assertThat(TestTlsClient.handshakeSucceeds("127.0.0.1", server.port(), trustManager)).isTrue();
        }
    }

    @Test
    void theNoSocketOverloadIsHostIndeterminateAndAlwaysFallsToTheList() throws Exception {
        var vaultCa = TestCa.selfSigned("Vault CA");
        var vaultLeaf = vaultCa.issueLeaf("vault");
        var vaultTrust = VaultEndpointTrust.from("https://vault:8200", writeCaCert(vaultCa), monitor).orElseThrow();
        // A list that would reject the vault CA, so the assertion can tell "routed to the vault exception" apart
        // from "routed to the list": if this overload ever consulted the vault exception, the call would succeed.
        var trustManager = new DsTlsCompositeTrustManager(vaultTrust, RejectAllTrustManager.INSTANCE);

        assertThatThrownBy(() -> trustManager.checkServerTrusted(vaultLeaf.chain(), "RSA"))
                .isInstanceOf(CertificateException.class);
    }

    @Test
    void aNonSslSocketIsHostIndeterminateAndAlwaysFallsToTheList() throws Exception {
        var vaultCa = TestCa.selfSigned("Vault CA");
        var vaultLeaf = vaultCa.issueLeaf("vault");
        var vaultTrust = VaultEndpointTrust.from("https://vault:8200", writeCaCert(vaultCa), monitor).orElseThrow();
        var trustManager = new DsTlsCompositeTrustManager(vaultTrust, RejectAllTrustManager.INSTANCE);

        try (var plainSocket = new Socket()) {
            assertThatThrownBy(() -> trustManager.checkServerTrusted(vaultLeaf.chain(), "RSA", plainSocket))
                    .isInstanceOf(CertificateException.class);
        }
    }

    private static DsTlsCompositeTrustManager compositeOf(X509ExtendedTrustManager listTrustManager) {
        return new DsTlsCompositeTrustManager(null, listTrustManager);
    }

    private static X509ExtendedTrustManager listOnly(TestCa... approvedCas) throws Exception {
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        var index = 0;
        for (var ca : approvedCas) {
            keyStore.setCertificateEntry("ca-" + index++, ca.certificate());
        }
        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        for (var candidate : trustManagerFactory.getTrustManagers()) {
            if (candidate instanceof X509ExtendedTrustManager extended) {
                return extended;
            }
        }
        throw new IllegalStateException("no X509ExtendedTrustManager produced");
    }

    private String writeCaCert(TestCa ca) throws Exception {
        var path = tempDir.resolve("vault-ca-" + System.nanoTime() + ".pem");
        try (var writer = new PemWriter(new FileWriter(path.toFile()))) {
            writer.writeObject(new PemObject("CERTIFICATE", ca.certificate().getEncoded()));
        }
        return path.toString();
    }
}

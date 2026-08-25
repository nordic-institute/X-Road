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

import java.io.FileWriter;
import java.nio.file.Path;
import java.security.cert.CertificateException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class VaultEndpointTrustTest {

    @Mock
    private Monitor monitor;

    @TempDir
    private Path tempDir;

    @Test
    void isAbsentWhenVaultUrlIsBlank() {
        assertThat(VaultEndpointTrust.from(" ", "/some/path", monitor)).isEmpty();
    }

    @Test
    void isAbsentWhenCaCertPathIsBlank() {
        assertThat(VaultEndpointTrust.from("https://vault:8200", " ", monitor)).isEmpty();
    }

    @Test
    void isAbsentWhenTheVaultUrlIsUnparseable() {
        assertThat(VaultEndpointTrust.from("://not a url", "/some/path", monitor)).isEmpty();
    }

    @Test
    void isAbsentWhenTheSchemeHasNoDefaultPort() throws Exception {
        var caCertPath = writeCaCert(TestCa.selfSigned("Vault CA"));
        assertThat(VaultEndpointTrust.from("ftp://vault", caCertPath, monitor)).isEmpty();
    }

    @Test
    void isAbsentWhenTheCaCertFileDoesNotExist() {
        assertThat(VaultEndpointTrust.from("https://vault:8200", tempDir.resolve("missing.pem").toString(), monitor)).isEmpty();
    }

    @Test
    void resolvesTheDefaultHttpsPortWhenOmitted() throws Exception {
        var caCertPath = writeCaCert(TestCa.selfSigned("Vault CA"));

        var vaultTrust = VaultEndpointTrust.from("https://vault", caCertPath, monitor).orElseThrow();

        assertThat(vaultTrust.matchesVaultEndpoint("vault", 443)).isTrue();
        assertThat(vaultTrust.matchesVaultEndpoint("vault", 8200)).isFalse();
    }

    @Test
    void resolvesTheDefaultHttpPortWhenOmitted() throws Exception {
        var caCertPath = writeCaCert(TestCa.selfSigned("Vault CA"));

        var vaultTrust = VaultEndpointTrust.from("http://vault", caCertPath, monitor).orElseThrow();

        assertThat(vaultTrust.matchesVaultEndpoint("vault", 80)).isTrue();
    }

    @Test
    void keepsAnExplicitPort() throws Exception {
        var caCertPath = writeCaCert(TestCa.selfSigned("Vault CA"));

        var vaultTrust = VaultEndpointTrust.from("https://vault:8200", caCertPath, monitor).orElseThrow();

        assertThat(vaultTrust.matchesVaultEndpoint("vault", 8200)).isTrue();
        assertThat(vaultTrust.matchesVaultEndpoint("vault", 443)).isFalse();
    }

    @Test
    void matchesTheHostCaseInsensitively() throws Exception {
        var caCertPath = writeCaCert(TestCa.selfSigned("Vault CA"));

        var vaultTrust = VaultEndpointTrust.from("https://Vault.Example:8200", caCertPath, monitor).orElseThrow();

        assertThat(vaultTrust.matchesVaultEndpoint("vault.example", 8200)).isTrue();
    }

    @Test
    void checkServerTrustedAcceptsAChainFromTheConfiguredCa() throws Exception {
        var ca = TestCa.selfSigned("Vault CA");
        var caCertPath = writeCaCert(ca);
        var leaf = ca.issueLeaf("vault");

        var vaultTrust = VaultEndpointTrust.from("https://vault:8200", caCertPath, monitor).orElseThrow();

        assertThatCode(() -> vaultTrust.checkServerTrusted(leaf.chain(), "RSA")).doesNotThrowAnyException();
    }

    @Test
    void checkServerTrustedRejectsAChainFromADifferentCa() throws Exception {
        var vaultCa = TestCa.selfSigned("Vault CA");
        var otherCa = TestCa.selfSigned("Other CA");
        var caCertPath = writeCaCert(vaultCa);
        var otherLeaf = otherCa.issueLeaf("vault");

        var vaultTrust = VaultEndpointTrust.from("https://vault:8200", caCertPath, monitor).orElseThrow();

        assertThatThrownBy(() -> vaultTrust.checkServerTrusted(otherLeaf.chain(), "RSA"))
                .isInstanceOf(CertificateException.class);
    }

    private String writeCaCert(TestCa ca) throws Exception {
        var path = tempDir.resolve("vault-ca-" + System.nanoTime() + ".pem");
        try (var writer = new PemWriter(new FileWriter(path.toFile()))) {
            writer.writeObject(new PemObject("CERTIFICATE", ca.certificate().getEncoded()));
        }
        return path.toString();
    }
}

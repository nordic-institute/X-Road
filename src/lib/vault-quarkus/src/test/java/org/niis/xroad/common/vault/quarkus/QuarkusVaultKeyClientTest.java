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
package org.niis.xroad.common.vault.quarkus;

import ee.ria.xroad.common.util.CryptoUtils;

import io.quarkus.vault.VaultPKISecretEngine;
import io.quarkus.vault.VaultPKISecretEngineFactory;
import io.quarkus.vault.client.VaultClientException;
import io.quarkus.vault.pki.CertificateData;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.config.CertificateProvisioningConfig;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.vault.VaultKeyClient.INTERNAL_RPC_CA_READ_FAILED;

@ExtendWith(MockitoExtension.class)
class QuarkusVaultKeyClientTest {

    private static final String PKI_PATH = "xrd-pki";

    @Mock
    private VaultPKISecretEngineFactory pkiSecretEngineFactory;
    @Mock
    private VaultPKISecretEngine pkiSecretEngine;

    private QuarkusVaultKeyClient keyClient;

    @BeforeEach
    void setUp() {
        when(pkiSecretEngineFactory.engine(PKI_PATH)).thenReturn(pkiSecretEngine);
        var properties = new CertificateProvisioningConfig("xrd-internal", "localhost", List.of(), List.of(), Duration.ofDays(1), PKI_PATH);
        keyClient = new QuarkusVaultKeyClient(pkiSecretEngineFactory, properties);
    }

    @Test
    void readInternalRpcCaShouldReturnTheCertificateAuthorityOfThePkiMount() throws Exception {
        var ca = selfSignedCa();
        when(pkiSecretEngine.getCertificateAuthority()).thenReturn(new CertificateData.PEM(toPem(ca)));

        assertThat(keyClient.readInternalRpcCa()).contains(ca);
    }

    @Test
    void readInternalRpcCaShouldFailWhenTheVaultReturnsUnparsableData() {
        when(pkiSecretEngine.getCertificateAuthority()).thenReturn(new CertificateData.PEM("not a certificate"));

        assertThatThrownBy(() -> keyClient.readInternalRpcCa())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(INTERNAL_RPC_CA_READ_FAILED);
    }

    @Test
    void readInternalRpcCaShouldFailWhenTheVaultReturnsBlankData() {
        when(pkiSecretEngine.getCertificateAuthority()).thenReturn(new CertificateData.PEM(" "));

        assertThatThrownBy(() -> keyClient.readInternalRpcCa())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(INTERNAL_RPC_CA_READ_FAILED);
    }

    @Test
    void readInternalRpcCaShouldFailWhenTheVaultReturnsNoCertificateData() {
        when(pkiSecretEngine.getCertificateAuthority()).thenReturn(new CertificateData.PEM(null));

        assertThatThrownBy(() -> keyClient.readInternalRpcCa())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(INTERNAL_RPC_CA_READ_FAILED);
    }

    @Test
    void readInternalRpcCaShouldFailWhenTheVaultReturnsInvalidBase64() {
        when(pkiSecretEngine.getCertificateAuthority()).thenReturn(
                new CertificateData.PEM("-----BEGIN CERTIFICATE-----\nAB=C\n-----END CERTIFICATE-----\n"));

        assertThatThrownBy(() -> keyClient.readInternalRpcCa())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(INTERNAL_RPC_CA_READ_FAILED);
    }

    @Test
    void readInternalRpcCaShouldFailWhenTheVaultReturnsNothing() {
        when(pkiSecretEngine.getCertificateAuthority()).thenReturn(null);

        assertThatThrownBy(() -> keyClient.readInternalRpcCa())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(INTERNAL_RPC_CA_READ_FAILED);
    }

    @Test
    void readInternalRpcCaShouldFailWhenTheVaultRefusesTheRead() {
        when(pkiSecretEngine.getCertificateAuthority()).thenThrow(
                new VaultClientException("read", "xrd-pki/cert/ca", 403, "permission denied", null));

        assertThatThrownBy(() -> keyClient.readInternalRpcCa())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(INTERNAL_RPC_CA_READ_FAILED);
    }

    private static X509Certificate selfSignedCa() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        var subject = new X500Name("CN=Test Internal RPC CA");
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE, Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)), subject, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static String toPem(X509Certificate certificate) throws Exception {
        var out = new ByteArrayOutputStream();
        CryptoUtils.writeCertificatePem(certificate.getEncoded(), out);
        return out.toString(StandardCharsets.UTF_8);
    }
}

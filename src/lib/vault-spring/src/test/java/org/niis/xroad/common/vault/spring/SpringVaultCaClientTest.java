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
package org.niis.xroad.common.vault.spring;

import ee.ria.xroad.common.util.CryptoUtils;

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
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.vault.VaultCaClient.CA_READ_FAILED;

@ExtendWith(MockitoExtension.class)
class SpringVaultCaClientTest {

    private static final String PKI_PATH = "test-pki";
    private static final String CA_PATH = PKI_PATH + "/cert/ca";

    @Mock
    private VaultTemplate vaultTemplate;
    private SpringVaultCaClient caClient;

    @BeforeEach
    void setUp() {
        var properties = new CertificateProvisioningConfig("xrd-internal", "localhost", List.of(), List.of(), Duration.ofDays(1), PKI_PATH);
        caClient = new SpringVaultCaClient(vaultTemplate, properties);
    }

    @Test
    void readCaCertificateShouldReturnTheCertificateAuthorityOfThePkiMount() throws Exception {
        var ca = selfSignedCa();
        when(vaultTemplate.read(CA_PATH)).thenReturn(responseWithCertificate(toPem(ca)));

        assertThat(caClient.readCaCertificate()).contains(ca);
    }

    @Test
    void readCaCertificateShouldFailWhenTheVaultReturnsUnparsableData() {
        when(vaultTemplate.read(CA_PATH)).thenReturn(responseWithCertificate("not a certificate"));

        assertReadFails();
    }

    @Test
    void readCaCertificateShouldFailWhenTheVaultReturnsBlankData() {
        when(vaultTemplate.read(CA_PATH)).thenReturn(responseWithCertificate(" "));

        assertReadFails();
    }

    @Test
    void readCaCertificateShouldFailWhenTheCertificateFieldIsMissing() {
        var response = new VaultResponse();
        response.setData(Map.of("other", "value"));
        when(vaultTemplate.read(CA_PATH)).thenReturn(response);

        assertReadFails();
    }

    @Test
    void readCaCertificateShouldFailWhenTheVaultReturnsNothing() {
        when(vaultTemplate.read(CA_PATH)).thenReturn(null);

        assertReadFails();
    }

    @Test
    void readCaCertificateShouldFailWhenTheVaultRefusesTheRead() {
        when(vaultTemplate.read(CA_PATH)).thenThrow(new VaultException("permission denied"));

        assertReadFails();
    }

    private void assertReadFails() {
        assertThatThrownBy(() -> caClient.readCaCertificate())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(CA_READ_FAILED);
    }

    private static VaultResponse responseWithCertificate(String pem) {
        var response = new VaultResponse();
        response.setData(Map.of("certificate", pem));
        return response;
    }

    private static X509Certificate selfSignedCa() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        var subject = new X500Name("CN=Test CA");
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

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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CryptoUtils;

import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.VaultKeyClient;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.KeyUsageConverter;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagementServiceTlsCertificateServiceImplTest {
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private VaultClient vaultClient;
    @Mock
    private VaultKeyClient vaultKeyClient;

    private ManagementServiceTlsCertificateServiceImpl service;

    @BeforeEach
    void setup() {
        CertificateConverter certificateConverter = new CertificateConverter(new KeyUsageConverter());
        service = new ManagementServiceTlsCertificateServiceImpl(vaultClient, vaultKeyClient, certificateConverter, auditDataHelper);
    }

    @Test
    void generateCsr() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        when(vaultClient.getManagementServicesTlsCredentials()).thenReturn(
                mockTlsCredentials("src/test/resources/ssl/management-service.key", "src/test/resources/ssl/management-service.crt"));
        var csr = service.generateCsr("CN=TEST");
        try (PemReader pemReader = new PemReader(new InputStreamReader(new ByteArrayInputStream(csr)))) {
            byte[] csrBytes = pemReader.readPemObject().getContent();
            assertThat(new PKCS10CertificationRequest(csrBytes)).isNotNull();
        }
    }

    @Test
    void generateCsrShouldThrownValidationFailureException()
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        when(vaultClient.getManagementServicesTlsCredentials()).thenReturn(
                mockTlsCredentials("src/test/resources/ssl/management-service.key", "src/test/resources/ssl/management-service.crt"));
        assertThatThrownBy(() -> service.generateCsr("TEST"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=invalid_distinguished_name]");
    }

    @Test
    void importTlsCertificateShouldThrownValidationFailureException()
            throws CertificateEncodingException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        when(vaultClient.getManagementServicesTlsCredentials()).thenReturn(
                mockTlsCredentials("src/test/resources/ssl/management-service.key", "src/test/resources/ssl/management-service.crt"));
        var certificateBytes = service.getTlsCertificate().getEncoded();
        assertThatThrownBy(() -> service.importTlsCertificate(certificateBytes))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=certificate_already_exists]");
    }

    @Test
    void importTlsCertificateShouldThrownValidationFailureException2() {
        byte[] certificateBytes = new byte[]{1, 2, 3};
        assertThatThrownBy(() -> service.importTlsCertificate(certificateBytes))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=cannot_convert_bytes_to_certificate]");
    }

    @Test
    void importTlsCertificateShouldThrownValidationFailureException3()
            throws IOException, CertificateEncodingException, NoSuchAlgorithmException, InvalidKeySpecException {
        when(vaultClient.getManagementServicesTlsCredentials()).thenReturn(
                mockTlsCredentials("src/test/resources/ssl/management-service.key", "src/test/resources/ssl/management-service.crt"));
        var certificate = CryptoUtils.readCertificate(
                Files.readAllBytes(Path.of("src/test/resources/ssl/invalid-chain.crt"))
        );
        var certificateBytes = certificate.getEncoded();
        assertThatThrownBy(() -> service.importTlsCertificate(certificateBytes))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=key_not_found]");
    }

    @Test
    void generateTlsKeyAndCertificate() throws CertificateException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        when(vaultKeyClient.provisionNewCerts()).thenReturn(
                mockVaultKeyCreation("src/test/resources/ssl/management-service.key", "src/test/resources/ssl/management-service.crt"));
        when(vaultClient.getManagementServicesTlsCredentials()).thenReturn(
                mockTlsCredentials("src/test/resources/ssl/management-service.key", "src/test/resources/ssl/management-service.crt"));
        assertDoesNotThrow(() -> service.generateTlsKeyAndCertificate());
    }

    private InternalSSLKey mockTlsCredentials(String privateKeyPath, String certificatePath)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        var privateKey = CryptoUtils.getPrivateKey(Files.newInputStream(Path.of(privateKeyPath)));
        var certChain = CryptoUtils.readCertificates(Files.readAllBytes(Path.of(certificatePath)));
        return new InternalSSLKey(privateKey, certChain.toArray(new X509Certificate[0]));
    }

    private VaultKeyClient.VaultKeyData mockVaultKeyCreation(String privateKeyPath, String certificatePath)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        var privateKey = CryptoUtils.getPrivateKey(Files.newInputStream(Path.of(privateKeyPath)));
        var certChain = CryptoUtils.readCertificates(Files.readAllBytes(Path.of(certificatePath)));
        return new VaultKeyClient.VaultKeyData(
                certChain.toArray(new X509Certificate[0]), privateKey, certChain.toArray(new X509Certificate[0]));
    }
}

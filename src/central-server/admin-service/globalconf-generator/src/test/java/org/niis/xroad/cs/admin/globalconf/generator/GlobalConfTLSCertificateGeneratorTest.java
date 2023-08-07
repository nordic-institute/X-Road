/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GlobalConfTLSCertificateGeneratorTest {

    @Mock
    SignerProxyFacade signerProxyFacade;
    @Mock
    SystemParameterService systemParameterService;
    @InjectMocks
    GlobalConfTLSCertificateGenerator tlsCertificateGenerator;

    private static final String INTERNAL_KEY_ID = "INTERNAL-KEY-ID";
    private static final String EXTERNAL_KEY_ID = "EXTERNAL-KEY-ID";
    private static final String CENTRAL_SERVICE = "cs";
    private final Path internalConfCrtPath = Path.of(SystemProperties.getConfPath() + "ssl/internal-conf.crt");
    private final Path externalConfCrtPath = Path.of(SystemProperties.getConfPath() + "ssl/external-conf.crt");
    private final Path initialInternalConfCrtPath = Path.of(SystemProperties.getConfPath() + "initial-internal-conf.crt");
    private final Path initialExternalConfCrtPath = Path.of(SystemProperties.getConfPath() + "initial-external-conf.crt");
    private final Path signedInternalConfCrtPath = Path.of(SystemProperties.getConfPath() + "signed-internal-conf.crt");
    private final Path signedExternalConfCrtPath = Path.of(SystemProperties.getConfPath() + "signed-external-conf.crt");
    private final Path internalSigningCrtPath = Path.of(SystemProperties.getConfPath() + "internal-signing.crt");
    private final Path externalSigningCrtPath = Path.of(SystemProperties.getConfPath() + "external-signing.crt");
    private final Path centerAdminServiceCrtPath = Path.of(SystemProperties.getConfPath() + "ssl/center-admin-service.crt");

    @BeforeAll
    public static void setup() {
        System.setProperty(SystemProperties.CONF_PATH, "src/test/resources/");
    }

    @AfterEach
    public void after() throws Exception {
        Files.deleteIfExists(internalConfCrtPath);
        Files.deleteIfExists(externalConfCrtPath);
    }

    @SneakyThrows
    @Test
    void shouldUpdateGlobalConfTLSCertificates() {
        Files.copy(initialInternalConfCrtPath, internalConfCrtPath, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(initialExternalConfCrtPath, externalConfCrtPath, StandardCopyOption.REPLACE_EXISTING);
        when(signerProxyFacade.getSignMechanism(INTERNAL_KEY_ID)).thenReturn(CryptoUtils.CKM_RSA_PKCS_NAME);
        when(signerProxyFacade.getSignMechanism(EXTERNAL_KEY_ID)).thenReturn(CryptoUtils.CKM_RSA_PKCS_NAME);
        X509Certificate signedInternalConfCert = getX509Certificate(signedInternalConfCrtPath);
        X509Certificate signedExternalConfCert = getX509Certificate(signedExternalConfCrtPath);
        X509Certificate certToSign = getX509Certificate(centerAdminServiceCrtPath);
        PublicKey publicKeyToSign = certToSign.getPublicKey();
        when(signerProxyFacade.signCertificate(INTERNAL_KEY_ID, CryptoUtils.SHA512WITHRSA_ID, "CN=" + CENTRAL_SERVICE, publicKeyToSign))
                .thenReturn(signedInternalConfCert.getEncoded(), signedExternalConfCert.getEncoded());
        when(signerProxyFacade.signCertificate(EXTERNAL_KEY_ID, CryptoUtils.SHA512WITHRSA_ID, "CN=" + CENTRAL_SERVICE, publicKeyToSign))
                .thenReturn(signedExternalConfCert.getEncoded());
        when(systemParameterService.getConfSignDigestAlgoId()).thenReturn(CryptoUtils.SHA512_ID);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVICE);
        ConfigurationSigningKey internalConfSigningKey =
                getConfigurationSigningKey(INTERNAL_KEY_ID, internalSigningCrtPath);
        ConfigurationSigningKey externalConfSigningKey =
                getConfigurationSigningKey(EXTERNAL_KEY_ID, externalSigningCrtPath);

        tlsCertificateGenerator.updateGlobalConfTLSCertificates(internalConfSigningKey, externalConfSigningKey);

        verify(signerProxyFacade)
                .signCertificate(INTERNAL_KEY_ID, CryptoUtils.SHA512WITHRSA_ID, "CN=" + CENTRAL_SERVICE, publicKeyToSign);
        verify(signerProxyFacade)
                .signCertificate(EXTERNAL_KEY_ID, CryptoUtils.SHA512WITHRSA_ID, "CN=" + CENTRAL_SERVICE, publicKeyToSign);

        X509Certificate internalConfCrt = getX509Certificate(internalConfCrtPath);
        assertThat(signedInternalConfCert.equals(internalConfCrt)).isTrue();
        X509Certificate externalConfCrt = getX509Certificate(externalConfCrtPath);
        assertThat(signedExternalConfCert.equals(externalConfCrt)).isTrue();
    }

    @SneakyThrows
    @Test
    void shouldNotUpdateGlobalConfTLSCertificates() {
        Files.copy(signedInternalConfCrtPath, internalConfCrtPath, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(signedExternalConfCrtPath, externalConfCrtPath, StandardCopyOption.REPLACE_EXISTING);
        ConfigurationSigningKey internalConfSigningKey =
                getConfigurationSigningKey(INTERNAL_KEY_ID, internalSigningCrtPath);
        ConfigurationSigningKey externalConfSigningKey =
                getConfigurationSigningKey(EXTERNAL_KEY_ID, externalSigningCrtPath);

        tlsCertificateGenerator.updateGlobalConfTLSCertificates(internalConfSigningKey, externalConfSigningKey);

        verify(signerProxyFacade, never()).getSignMechanism(any());
        verify(signerProxyFacade, never()).signCertificate(any(), any(), any(), any());
    }

    private static X509Certificate getX509Certificate(Path certPath) throws IOException {
        return readCertificate(Files.readAllBytes(certPath));
    }

    private static ConfigurationSigningKey getConfigurationSigningKey(String keyId, Path signingKeyCert) throws IOException {
        ConfigurationSigningKey confSigningKey = new ConfigurationSigningKey();
        confSigningKey.setKeyIdentifier(keyId);
        confSigningKey.setCert(Files.readAllBytes(signingKeyCert));
        return confSigningKey;
    }

}

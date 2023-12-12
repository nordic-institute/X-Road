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

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.KeyUsageConverter;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ManagementServiceTlsCertificateServiceImplTest {
    @Mock
    private AuditDataHelper auditDataHelper;
    private ManagementServiceTlsCertificateServiceImpl service;

    @BeforeEach
    void setup() {
        ExternalProcessRunner externalProcessRunner1 = new ExternalProcessRunner();
        CertificateConverter certificateConverter1 = new CertificateConverter(new KeyUsageConverter());
        final String generateCertificateScriptPath = "src/test/resources/scripts/generate_certificate.sh";
        final String certificatesPath = "src/test/resources/ssl/";

        service = new ManagementServiceTlsCertificateServiceImpl(
                certificateConverter1,
                externalProcessRunner1,
                auditDataHelper,
                generateCertificateScriptPath,
                certificatesPath
        );
    }

    @Test
    void getTlsCertificateShouldThrownValidationFailureException() {
        setupCertificatePathNotExists();
        assertThatThrownBy(() -> service.getTlsCertificate())
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Invalid X.509 certificate");
    }

    @Test
    void generateCsrShouldThrownValidationFailureException() {
        assertThatThrownBy(() -> service.generateCsr("TEST"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Invalid distinguished name");
    }

    @Test
    void importTlsCertificateShouldThrownValidationFailureException() throws CertificateEncodingException {
        var certificateBytes = service.getTlsCertificate().getEncoded();
        assertThatThrownBy(() -> service.importTlsCertificate(certificateBytes))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("The imported certificate already exists");
    }

    @Test
    void importTlsCertificateShouldThrownValidationFailureException2() {
        byte[] certificateBytes = new byte[]{1, 2, 3};
        assertThatThrownBy(() -> service.importTlsCertificate(certificateBytes))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Cannot convert bytes to certificate");
    }

    @Test
    void importTlsCertificateShouldThrownValidationFailureException3() throws IOException, CertificateEncodingException {
        var certificate = CryptoUtils.readCertificate(Files.readAllBytes(Path.of("src/test/resources/ssl/invalid-chain.crt")));
        var certificateBytes = certificate.getEncoded();
        assertThatThrownBy(() -> service.importTlsCertificate(certificateBytes))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("The imported certificate does not match the TLS key");
    }

    @Test
    void generateTlsKeyAndCertificateShouldThrownValidationFailureException() {
        assertThatThrownBy(() -> service.generateTlsKeyAndCertificate())
                .isInstanceOf(ServiceException.class)
                .hasMessage("Failed to generate TLS key and certificate");
    }

    private void setupCertificatePathNotExists() {
        ExternalProcessRunner externalProcessRunner1 = new ExternalProcessRunner();
        CertificateConverter certificateConverter1 = new CertificateConverter(new KeyUsageConverter());
        final String generateCertificateScriptPath = "src/test/resources/scripts/generate_certificate.sh";
        final String certificatesPath = "pathNotExists/";

        service = new ManagementServiceTlsCertificateServiceImpl(
                certificateConverter1,
                externalProcessRunner1,
                auditDataHelper,
                generateCertificateScriptPath,
                certificatesPath
        );
    }
}

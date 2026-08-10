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
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.KeyUsageConverter;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.validator.DsTlsMaterialValidator;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

@ExtendWith(MockitoExtension.class)
class DsTlsCertificateServiceImplTest {

    @Mock
    private VaultClient vaultClient;
    @Mock
    private DsTlsMaterialValidator dsTlsMaterialValidator;
    @Mock
    private AuditDataHelper auditDataHelper;

    private DsTlsCertificateServiceImpl service;

    @BeforeEach
    void setUp() {
        TimeUtils.setClock(Clock.systemDefaultZone());
        service = new DsTlsCertificateServiceImpl(
                vaultClient, dsTlsMaterialValidator, new CertificateConverter(new KeyUsageConverter()), auditDataHelper);
    }

    @AfterEach
    void tearDown() {
        TimeUtils.setClock(Clock.systemDefaultZone());
    }

    @Test
    void getCertificateDetailsShouldReturnStoredCertificate() throws Exception {
        X509Certificate certificate = selfSignedCertificate();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(null, new X509Certificate[]{certificate}));

        var details = service.getCertificateDetails();

        assertThat(details.getSerial()).isEqualTo(certificate.getSerialNumber().toString());
    }

    @Test
    void getCertificateDetailsShouldThrowNotFoundWhenSlotIsEmpty() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials())
                .thenThrow(XrdRuntimeException.systemException(MISSING_SECRET).build());

        assertThatThrownBy(() -> service.getCertificateDetails())
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCertificateDetailsShouldPropagateOtherVaultFailuresAsServerError() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.getCertificateDetails())
                .isInstanceOf(InternalServerErrorException.class);
    }

    @Test
    void uploadCertificateShouldStoreValidatedMaterialAndAuditTheHash() throws Exception {
        X509Certificate certificate = selfSignedCertificate();
        KeyPair keyPair = generateKeyPair();
        InternalSSLKey validated = new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[]{certificate});
        byte[] keyBytes = "key".getBytes();
        byte[] certBytes = "cert".getBytes();
        when(dsTlsMaterialValidator.validate(keyBytes, certBytes)).thenReturn(validated);

        var details = service.uploadCertificate(keyBytes, certBytes);

        verify(vaultClient).createDsHttpsTlsCredentials(validated);
        verify(auditDataHelper).putCertificateHash(certificate);
        assertThat(details.getSerial()).isEqualTo(certificate.getSerialNumber().toString());
    }

    private static X509Certificate selfSignedCertificate() throws Exception {
        return CertUtils.createSelfSignedCertificate("ds-tls-test", generateKeyPair(), 30)[0];
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}

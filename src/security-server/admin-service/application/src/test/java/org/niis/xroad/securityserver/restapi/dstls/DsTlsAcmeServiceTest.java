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
package org.niis.xroad.securityserver.restapi.dstls;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.acme.AcmeKeyPurpose;
import org.niis.xroad.common.acme.AcmeService;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.globalconf.model.DsTlsCaInfo;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DsTlsAcmeServiceTest {

    private static final String HOSTNAME = "ss.example.org";

    @Mock
    private AcmeService acmeService;
    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private AdminServiceProperties.Dataspace dataspace;

    private DsTlsAcmeService dsTlsAcmeService;

    private DsTlsAcmeService service() {
        if (dsTlsAcmeService == null) {
            lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
            dsTlsAcmeService = new DsTlsAcmeService(acmeService, adminServiceProperties);
        }
        return dsTlsAcmeService;
    }

    @Test
    void enrollShouldOrderUnderTheFixedAliasWithNoContactsWhenUnconfigured() {
        DsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        byte[] certRequest = {1, 2, 3};
        X509Certificate cert = mock(X509Certificate.class);
        when(acmeService.orderCertificateFromACMEServer(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cert));

        List<X509Certificate> result = service().enroll(caInfo, HOSTNAME, certRequest);

        assertThat(result).containsExactly(cert);

        ArgumentCaptor<ApprovedCAInfo> caInfoCaptor = ArgumentCaptor.forClass(ApprovedCAInfo.class);
        verify(acmeService).orderCertificateFromACMEServer(eq(HOSTNAME), eq(HOSTNAME), eq(AcmeKeyPurpose.AUTHENTICATION),
                caInfoCaptor.capture(), eq(DsTlsAcmeService.DS_TLS_ACME_ALIAS), eq(certRequest), eq(List.of()));

        assertThat(caInfoCaptor.getValue().getName()).isEqualTo("Test CA");
        assertThat(caInfoCaptor.getValue().getAcmeServerDirectoryUrl()).isEqualTo("http://testca:8887");
        assertThat(caInfoCaptor.getValue().getAuthenticationCertificateProfileId()).isNull();
        assertThat(caInfoCaptor.getValue().getSigningCertificateProfileId()).isNull();
    }

    @Test
    void enrollShouldPassTheConfiguredTlsCertificateContacts() {
        DsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getTlsCertificateContacts()).thenReturn(List.of("dstls@example.org"));
        when(acmeService.orderCertificateFromACMEServer(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(mock(X509Certificate.class)));

        service().enroll(caInfo, HOSTNAME, new byte[]{1, 2, 3});

        verify(acmeService).orderCertificateFromACMEServer(eq(HOSTNAME), eq(HOSTNAME), eq(AcmeKeyPurpose.AUTHENTICATION),
                any(ApprovedCAInfo.class), eq(DsTlsAcmeService.DS_TLS_ACME_ALIAS), any(), eq(List.of("dstls@example.org")));
    }

    @Test
    void renewShouldReferenceTheCurrentCertificateUnderTheFixedAlias() {
        DsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        X509Certificate currentCertificate = mock(X509Certificate.class);
        byte[] certRequest = {4, 5, 6};
        X509Certificate newCert = mock(X509Certificate.class);
        when(acmeService.renew(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(newCert));

        List<X509Certificate> result = service().renew(caInfo, HOSTNAME, currentCertificate, certRequest);

        assertThat(result).containsExactly(newCert);
        verify(acmeService).renew(eq(DsTlsAcmeService.DS_TLS_ACME_ALIAS), eq(HOSTNAME), any(ApprovedCAInfo.class),
                eq(AcmeKeyPurpose.AUTHENTICATION), eq(currentCertificate), eq(certRequest), eq(List.of()));
    }

    @Test
    void renewShouldPassTheConfiguredTlsCertificateContacts() {
        DsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getTlsCertificateContacts()).thenReturn(List.of("dstls@example.org"));
        when(acmeService.renew(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(mock(X509Certificate.class)));

        service().renew(caInfo, HOSTNAME, mock(X509Certificate.class), new byte[]{4, 5, 6});

        verify(acmeService).renew(eq(DsTlsAcmeService.DS_TLS_ACME_ALIAS), eq(HOSTNAME), any(ApprovedCAInfo.class),
                eq(AcmeKeyPurpose.AUTHENTICATION), any(), any(), eq(List.of("dstls@example.org")));
    }

    @Test
    void getNextRenewalTimeShouldDelegateToTheSharedEngine() {
        DsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        X509Certificate certificate = mock(X509Certificate.class);
        Instant expected = Instant.now().plusSeconds(3600);
        when(acmeService.getNextRenewalTime(any(), any(), any(), any(), any())).thenReturn(expected);

        Instant result = service().getNextRenewalTime(caInfo, certificate);

        assertThat(result).isEqualTo(expected);
        verify(acmeService).getNextRenewalTime(eq(DsTlsAcmeService.DS_TLS_ACME_ALIAS), any(ApprovedCAInfo.class),
                eq(certificate), eq(AcmeKeyPurpose.AUTHENTICATION), eq(List.of()));
    }

    private static DsTlsCaInfo dsTlsCaInfo(String name, String acmeServerDirectoryUrl) {
        return new DsTlsCaInfo(name, null, List.of(), acmeServerDirectoryUrl, null, null);
    }
}

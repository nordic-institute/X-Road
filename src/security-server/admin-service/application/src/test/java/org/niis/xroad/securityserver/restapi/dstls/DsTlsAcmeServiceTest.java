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
import org.niis.xroad.common.acme.AcmeAccountContext;
import org.niis.xroad.common.acme.AcmeClient;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
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
    private AcmeClient acmeClient;
    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private AdminServiceProperties.Dataspace dataspace;

    private DsTlsAcmeService dsTlsAcmeService;

    private DsTlsAcmeService service() {
        if (dsTlsAcmeService == null) {
            lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
            dsTlsAcmeService = new DsTlsAcmeService(acmeClient, adminServiceProperties);
        }
        return dsTlsAcmeService;
    }

    @Test
    void enrollShouldOrderUnderTheFixedAliasWithNoContactsWhenUnconfigured() {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        byte[] certRequest = {1, 2, 3};
        X509Certificate cert = mock(X509Certificate.class);
        when(acmeClient.orderCertificate(any(), any(), any(AcmeAccountContext.class), any()))
                .thenReturn(List.of(cert));

        List<X509Certificate> result = service().enroll(caInfo, HOSTNAME, certRequest);

        assertThat(result).containsExactly(cert);
        ArgumentCaptor<AcmeAccountContext> accountCaptor = ArgumentCaptor.forClass(AcmeAccountContext.class);
        verify(acmeClient).orderCertificate(eq(HOSTNAME), eq(HOSTNAME), accountCaptor.capture(), eq(certRequest));

        AcmeAccountContext account = accountCaptor.getValue();
        assertThat(account.accountAlias()).isEqualTo(DsTlsAcmeService.DS_TLS_ACME_ALIAS);
        assertThat(account.caName()).isEqualTo("Test CA");
        assertThat(account.acmeServerDirectoryUrl()).isEqualTo("http://testca:8887");
        assertThat(account.certificateProfileId()).isNull();
        assertThat(account.contacts()).isEmpty();
    }

    @Test
    void enrollShouldCarryTheConfiguredDsTlsCertificateProfileIdThrough() {
        ApprovedDsTlsCaInfo caInfo = new ApprovedDsTlsCaInfo("Test CA", null, List.of(), "http://testca:8887", null, "ds-tls-profile-id");
        when(acmeClient.orderCertificate(any(), any(), any(AcmeAccountContext.class), any()))
                .thenReturn(List.of(mock(X509Certificate.class)));

        service().enroll(caInfo, HOSTNAME, new byte[]{1, 2, 3});

        ArgumentCaptor<AcmeAccountContext> accountCaptor = ArgumentCaptor.forClass(AcmeAccountContext.class);
        verify(acmeClient).orderCertificate(any(), any(), accountCaptor.capture(), any());
        assertThat(accountCaptor.getValue().certificateProfileId()).isEqualTo("ds-tls-profile-id");
    }

    @Test
    void enrollShouldPassTheConfiguredTlsCertificateContacts() {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getTlsCertificateContacts()).thenReturn(List.of("dstls@example.org"));
        when(acmeClient.orderCertificate(any(), any(), any(AcmeAccountContext.class), any()))
                .thenReturn(List.of(mock(X509Certificate.class)));

        service().enroll(caInfo, HOSTNAME, new byte[]{1, 2, 3});

        ArgumentCaptor<AcmeAccountContext> accountCaptor = ArgumentCaptor.forClass(AcmeAccountContext.class);
        verify(acmeClient).orderCertificate(eq(HOSTNAME), eq(HOSTNAME), accountCaptor.capture(), any());
        assertThat(accountCaptor.getValue().contacts()).containsExactly("dstls@example.org");
    }

    @Test
    void renewShouldReferenceTheCurrentCertificateUnderTheFixedAlias() {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        X509Certificate currentCertificate = mock(X509Certificate.class);
        byte[] certRequest = {4, 5, 6};
        X509Certificate newCert = mock(X509Certificate.class);
        when(acmeClient.renew(any(AcmeAccountContext.class), any(), any(), any())).thenReturn(List.of(newCert));

        List<X509Certificate> result = service().renew(caInfo, HOSTNAME, currentCertificate, certRequest);

        assertThat(result).containsExactly(newCert);
        ArgumentCaptor<AcmeAccountContext> accountCaptor = ArgumentCaptor.forClass(AcmeAccountContext.class);
        verify(acmeClient).renew(accountCaptor.capture(), eq(HOSTNAME), eq(currentCertificate), eq(certRequest));
        assertThat(accountCaptor.getValue().accountAlias()).isEqualTo(DsTlsAcmeService.DS_TLS_ACME_ALIAS);
    }

    @Test
    void renewShouldPassTheConfiguredTlsCertificateContacts() {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getTlsCertificateContacts()).thenReturn(List.of("dstls@example.org"));
        when(acmeClient.renew(any(AcmeAccountContext.class), any(), any(), any()))
                .thenReturn(List.of(mock(X509Certificate.class)));

        service().renew(caInfo, HOSTNAME, mock(X509Certificate.class), new byte[]{4, 5, 6});

        ArgumentCaptor<AcmeAccountContext> accountCaptor = ArgumentCaptor.forClass(AcmeAccountContext.class);
        verify(acmeClient).renew(accountCaptor.capture(), eq(HOSTNAME), any(), any());
        assertThat(accountCaptor.getValue().contacts()).containsExactly("dstls@example.org");
    }

    @Test
    void getNextRenewalTimeShouldDelegateToTheSharedEngine() {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        X509Certificate certificate = mock(X509Certificate.class);
        Instant expected = Instant.now().plusSeconds(3600);
        when(acmeClient.getNextRenewalTime(any(AcmeAccountContext.class), any())).thenReturn(expected);

        Instant result = service().getNextRenewalTime(caInfo, certificate);

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<AcmeAccountContext> accountCaptor = ArgumentCaptor.forClass(AcmeAccountContext.class);
        verify(acmeClient).getNextRenewalTime(accountCaptor.capture(), eq(certificate));
        assertThat(accountCaptor.getValue().accountAlias()).isEqualTo(DsTlsAcmeService.DS_TLS_ACME_ALIAS);
        assertThat(accountCaptor.getValue().caName()).isEqualTo("Test CA");
    }

    private static ApprovedDsTlsCaInfo dsTlsCaInfo(String name, String acmeServerDirectoryUrl) {
        return new ApprovedDsTlsCaInfo(name, null, List.of(), acmeServerDirectoryUrl, null, null);
    }
}

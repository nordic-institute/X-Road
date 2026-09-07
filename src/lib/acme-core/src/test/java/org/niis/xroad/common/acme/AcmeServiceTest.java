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
package org.niis.xroad.common.acme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;

import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AcmeService} is a thin façade: resolves member/CA-specific facts into an {@link AcmeAccountContext} and
 * delegates to {@link AcmeClient}. The actual ACME mechanics are exercised in {@link AcmeClientTest}; this class
 * only proves the resolution and delegation.
 */
@ExtendWith(MockitoExtension.class)
class AcmeServiceTest {

    private static final String CA_NAME = "testca";
    private static final String MEMBER_ID = "MEMBER1";
    private static final String DIRECTORY_URL = "https://ca.example.org/acme/directory";

    @Mock
    private AcmeClient acmeClient;

    private AcmeService acmeService;

    private AcmeService service() {
        if (acmeService == null) {
            acmeService = new AcmeService(acmeClient);
        }
        return acmeService;
    }

    @Test
    void resolveCertificateProfileIdUsesSigningProfileIdForSigningKeyUsage() {
        ApprovedCAInfo memberCa = new ApprovedCAInfo(CA_NAME, false, null, null,
                DIRECTORY_URL, null, "auth-profile-id", "sign-profile-id");

        String profileId = AcmeService.resolveCertificateProfileId(memberCa, AcmeKeyPurpose.SIGNING);

        assertThat(profileId).isEqualTo("sign-profile-id");
    }

    @Test
    void resolveCertificateProfileIdUsesAuthenticationProfileIdForAuthenticationKeyUsage() {
        ApprovedCAInfo memberCa = new ApprovedCAInfo(CA_NAME, false, null, null,
                DIRECTORY_URL, null, "auth-profile-id", "sign-profile-id");

        String profileId = AcmeService.resolveCertificateProfileId(memberCa, AcmeKeyPurpose.AUTHENTICATION);

        assertThat(profileId).isEqualTo("auth-profile-id");
    }

    @Test
    void resolveCertificateProfileIdReturnsNullWhenNoMemberProfileIdIsConfigured() {
        ApprovedCAInfo memberCa = new ApprovedCAInfo(CA_NAME, false, null, null, DIRECTORY_URL, null, null, null);

        String profileId = AcmeService.resolveCertificateProfileId(memberCa, AcmeKeyPurpose.SIGNING);

        assertThat(profileId).isNull();
    }

    @Test
    void resolveCertificateProfileIdReturnsNullForAuthenticationWhenOnlySigningProfileIdIsConfigured() {
        ApprovedCAInfo signingOnlyCa = new ApprovedCAInfo(CA_NAME, false, null, null,
                DIRECTORY_URL, null, null, "sign-profile-id");

        String profileId = AcmeService.resolveCertificateProfileId(signingOnlyCa, AcmeKeyPurpose.AUTHENTICATION);

        assertThat(profileId).isNull();
    }

    @Test
    void resolveCertificateProfileIdReturnsNullForSigningWhenOnlyAuthenticationProfileIdIsConfigured() {
        ApprovedCAInfo authOnlyCa = new ApprovedCAInfo(CA_NAME, false, null, null,
                DIRECTORY_URL, null, "auth-profile-id", null);

        String profileId = AcmeService.resolveCertificateProfileId(authOnlyCa, AcmeKeyPurpose.SIGNING);

        assertThat(profileId).isNull();
    }

    @Test
    void orderCertificateFromACMEServerBuildsTheAccountContextAndDelegatesToTheClient() {
        ApprovedCAInfo caInfo = new ApprovedCAInfo(CA_NAME, false, null, null,
                DIRECTORY_URL, null, "auth-profile-id", "sign-profile-id");
        byte[] certRequest = {1, 2, 3};
        X509Certificate cert = mock(X509Certificate.class);
        when(acmeClient.orderCertificate(any(), any(), any(AcmeAccountContext.class), any())).thenReturn(List.of(cert));

        List<X509Certificate> result = service().orderCertificateFromACMEServer("ss1.example.org", "ss1.example.org",
                AcmeKeyPurpose.SIGNING, caInfo, MEMBER_ID, certRequest, List.of("member@example.org"));

        assertThat(result).containsExactly(cert);
        ArgumentCaptor<AcmeAccountContext> accountCaptor = ArgumentCaptor.forClass(AcmeAccountContext.class);
        verify(acmeClient).orderCertificate(eq("ss1.example.org"), eq("ss1.example.org"), accountCaptor.capture(), eq(certRequest));

        AcmeAccountContext account = accountCaptor.getValue();
        assertThat(account.accountAlias()).isEqualTo(MEMBER_ID);
        assertThat(account.caName()).isEqualTo(CA_NAME);
        assertThat(account.acmeServerDirectoryUrl()).isEqualTo(DIRECTORY_URL);
        assertThat(account.certificateProfileId()).isEqualTo("sign-profile-id");
        assertThat(account.keyUsage()).isEqualTo(AcmeKeyPurpose.SIGNING);
        assertThat(account.contacts()).containsExactly("member@example.org");
    }

    @Test
    void renewBuildsTheAccountContextAndDelegatesToTheClient() {
        ApprovedCAInfo caInfo = new ApprovedCAInfo(CA_NAME, false, null, null,
                DIRECTORY_URL, null, "auth-profile-id", "sign-profile-id");
        X509Certificate oldCertificate = mock(X509Certificate.class);
        byte[] newCsr = {4, 5, 6};
        X509Certificate newCert = mock(X509Certificate.class);
        when(acmeClient.renew(any(AcmeAccountContext.class), any(), any(), any())).thenReturn(List.of(newCert));

        List<X509Certificate> result = service().renew(MEMBER_ID, "ss1.example.org", caInfo, AcmeKeyPurpose.AUTHENTICATION,
                oldCertificate, newCsr, List.of());

        assertThat(result).containsExactly(newCert);
        ArgumentCaptor<AcmeAccountContext> accountCaptor = ArgumentCaptor.forClass(AcmeAccountContext.class);
        verify(acmeClient).renew(accountCaptor.capture(), eq("ss1.example.org"), eq(oldCertificate), eq(newCsr));
        assertThat(accountCaptor.getValue().certificateProfileId()).isEqualTo("auth-profile-id");
    }

    @Test
    void isExternalAccountBindingRequiredDelegatesDirectlyToTheClient() {
        when(acmeClient.isExternalAccountBindingRequired(DIRECTORY_URL)).thenReturn(true);

        boolean result = service().isExternalAccountBindingRequired(DIRECTORY_URL);

        assertThat(result).isTrue();
    }
}

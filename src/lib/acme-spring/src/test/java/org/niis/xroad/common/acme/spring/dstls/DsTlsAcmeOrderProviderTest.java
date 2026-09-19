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
package org.niis.xroad.common.acme.spring.dstls;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.niis.xroad.restapi.dstls.DsTlsAcmeAvailability;
import org.niis.xroad.restapi.dstls.DsTlsAcmeOrderResult;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CA_NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class DsTlsAcmeOrderProviderTest {

    private static final String HOSTNAME = "ss.example.org";

    @Mock
    private DsTlsAcmeHostContext hostContext;
    @Mock
    private DsTlsAcmeService dsTlsAcmeService;

    private DsTlsAcmeOrderProvider provider() {
        return new DsTlsAcmeOrderProvider(hostContext, dsTlsAcmeService);
    }

    @Test
    void availabilityShouldBeFalseWhenNoAcmeCapableCaIsDesignated() {
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(nonAcmeCa("No ACME CA")));
        when(hostContext.getPublicHostname()).thenReturn(HOSTNAME);

        DsTlsAcmeAvailability availability = provider().getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.caNames()).isEmpty();
        assertThat(availability.publicHostname()).isEqualTo(HOSTNAME);
    }

    @Test
    void availabilityShouldBeFalseWhenTheHostnameDoesNotResolve() {
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(acmeCa("Test CA")));
        when(hostContext.getPublicHostname()).thenReturn(null);

        DsTlsAcmeAvailability availability = provider().getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.caNames()).containsExactly("Test CA");
        assertThat(availability.publicHostname()).isNull();
    }

    @Test
    void availabilityShouldBeFalseWhenHostnameResolutionThrows() {
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(acmeCa("Test CA")));
        when(hostContext.getPublicHostname()).thenThrow(new IllegalArgumentException("malformed"));

        DsTlsAcmeAvailability availability = provider().getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.publicHostname()).isNull();
    }

    @Test
    void availabilityShouldBeTrueWithOneAcmeCapableCa() {
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(acmeCa("Test CA")));
        when(hostContext.getPublicHostname()).thenReturn(HOSTNAME);

        DsTlsAcmeAvailability availability = provider().getAvailability();

        assertThat(availability.available()).isTrue();
        assertThat(availability.caNames()).containsExactly("Test CA");
    }

    @Test
    void availabilityShouldListEveryAcmeCapableCaWhenSeveralAreDesignated() {
        when(hostContext.getDsTlsCertificationAuthorities())
                .thenReturn(List.of(acmeCa("CA one"), acmeCa("CA two"), nonAcmeCa("Manual only CA")));
        when(hostContext.getPublicHostname()).thenReturn(HOSTNAME);

        DsTlsAcmeAvailability availability = provider().getAvailability();

        assertThat(availability.available()).isTrue();
        assertThat(availability.caNames()).containsExactlyInAnyOrder("CA one", "CA two");
    }

    @Test
    void orderShouldRejectAnUnknownCaName() throws Exception {
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(acmeCa("Known CA")));
        KeyPair keyPair = generateRsaKeyPair();

        assertThatThrownBy(() -> provider().order("Unknown CA", "CN=ds.example.org", "ds.example.org",
                keyPair.getPrivate(), keyPair.getPublic(), null))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code())
                        .isEqualTo(DS_TLS_CA_NOT_FOUND.code()));
        verify(dsTlsAcmeService, never()).enroll(any(), any(), any());
    }

    @Test
    void orderShouldRejectACaWithoutAnAcmeServer() throws Exception {
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(nonAcmeCa("Manual only CA")));
        KeyPair keyPair = generateRsaKeyPair();

        assertThatThrownBy(() -> provider().order("Manual only CA", "CN=ds.example.org", "ds.example.org",
                keyPair.getPrivate(), keyPair.getPublic(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void orderShouldEnrollWhenNoCertificateExistsYet() throws Exception {
        ApprovedDsTlsCaInfo caInfo = acmeCa("Test CA");
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate issued = mock(X509Certificate.class);
        Instant nextRenewalTime = Instant.now().plusSeconds(3600);
        when(dsTlsAcmeService.enroll(eq(caInfo), eq("ds.example.org"), any())).thenReturn(List.of(issued));
        when(dsTlsAcmeService.getNextRenewalTime(caInfo, issued)).thenReturn(nextRenewalTime);

        DsTlsAcmeOrderResult result = provider().order("Test CA", "CN=ds.example.org", "ds.example.org",
                keyPair.getPrivate(), keyPair.getPublic(), null);

        assertThat(result.certificateChain()).containsExactly(issued);
        assertThat(result.nextRenewalTime()).isEqualTo(nextRenewalTime);
        verify(dsTlsAcmeService, never()).renew(any(), any(), any(), any());
    }

    @Test
    void orderShouldRenewWhenACertificateAlreadyExists() throws Exception {
        ApprovedDsTlsCaInfo caInfo = acmeCa("Test CA");
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate currentCertificate = mock(X509Certificate.class);
        X509Certificate issued = mock(X509Certificate.class);
        when(dsTlsAcmeService.renew(eq(caInfo), eq("ds.example.org"), eq(currentCertificate), any()))
                .thenReturn(List.of(issued));
        when(dsTlsAcmeService.getNextRenewalTime(caInfo, issued)).thenReturn(Instant.now());

        DsTlsAcmeOrderResult result = provider().order("Test CA", "CN=ds.example.org", "ds.example.org",
                keyPair.getPrivate(), keyPair.getPublic(), currentCertificate);

        assertThat(result.certificateChain()).containsExactly(issued);
        verify(dsTlsAcmeService, never()).enroll(any(), any(), any());
    }

    @Test
    void orderShouldFailFastWhenTheAcmeServerReturnsNoCertificate() throws Exception {
        ApprovedDsTlsCaInfo caInfo = acmeCa("Test CA");
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        KeyPair keyPair = generateRsaKeyPair();
        when(dsTlsAcmeService.enroll(eq(caInfo), eq("ds.example.org"), any())).thenReturn(List.of());

        assertThatThrownBy(() -> provider().order("Test CA", "CN=ds.example.org", "ds.example.org",
                keyPair.getPrivate(), keyPair.getPublic(), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void orderShouldPropagateAnInvalidDistinguishedNameAsIs() throws Exception {
        ApprovedDsTlsCaInfo caInfo = acmeCa("Test CA");
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        KeyPair keyPair = generateRsaKeyPair();

        assertThatThrownBy(() -> provider().order("Test CA", "not a distinguished name", "ds.example.org",
                keyPair.getPrivate(), keyPair.getPublic(), null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dsTlsAcmeService, never()).enroll(any(), any(), any());
    }

    private static ApprovedDsTlsCaInfo acmeCa(String name) {
        return new ApprovedDsTlsCaInfo(name, null, List.of(), "http://testca:8887", null, null);
    }

    private static ApprovedDsTlsCaInfo nonAcmeCa(String name) {
        return new ApprovedDsTlsCaInfo(name, null, List.of(), null, null, null);
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}

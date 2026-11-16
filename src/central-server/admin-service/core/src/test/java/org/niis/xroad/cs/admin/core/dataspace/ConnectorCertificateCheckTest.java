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
package org.niis.xroad.cs.admin.core.dataspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.restapi.dstls.DsTlsCertificateStatus;
import org.niis.xroad.restapi.service.DsTlsCertificateService;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorCertificateCheckTest {

    private static final String HOST = "issuer.example.org";
    private static final int GENERAL_NAME_DNS = 2;

    @Mock
    private DsTlsCertificateService dsTlsCertificateService;

    private ConnectorCertificateCheck connectorCertificateCheck;

    @BeforeEach
    void setUp() {
        connectorCertificateCheck = new ConnectorCertificateCheck(dsTlsCertificateService);
    }

    @Test
    void isNotReadyWhenNoCertificateIsStored() {
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, null));

        assertThat(connectorCertificateCheck.isReadyFor(HOST)).isFalse();
    }

    @Test
    void isNotReadyWhenTheCertificateIsExpired() throws Exception {
        var certificate = mock(X509Certificate.class);
        doThrow(new CertificateExpiredException("expired")).when(certificate).checkValidity();
        lenient().when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(GENERAL_NAME_DNS, HOST)));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, certificate));

        assertThat(connectorCertificateCheck.isReadyFor(HOST)).isFalse();
    }

    @Test
    void isNotReadyWhenTheCertificateDoesNotCoverTheIssuerHost() throws CertificateParsingException {
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(GENERAL_NAME_DNS, "other.example.org")));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, certificate));

        assertThat(connectorCertificateCheck.isReadyFor(HOST)).isFalse();
    }

    @Test
    void isReadyWhenTheCertificateIsValidAndCoversTheIssuerHost() throws CertificateParsingException {
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(GENERAL_NAME_DNS, HOST)));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, certificate));

        assertThat(connectorCertificateCheck.isReadyFor(HOST)).isTrue();
    }

    @Test
    void isReadyWhenTheCertificateHasAMatchingWildcardSubjectAlternativeName() throws CertificateParsingException {
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(GENERAL_NAME_DNS, "*.example.org")));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, certificate));

        assertThat(connectorCertificateCheck.isReadyFor(HOST)).isTrue();
    }

    @Test
    void isReadyWhenTheWildcardMatchIsCaseInsensitive() throws CertificateParsingException {
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(GENERAL_NAME_DNS, "*.Example.ORG")));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, certificate));

        assertThat(connectorCertificateCheck.isReadyFor("ISSUER.example.org")).isTrue();
    }

    @Test
    void isNotReadyWhenTheCertificateWithAMatchingWildcardIsExpired() throws Exception {
        var certificate = mock(X509Certificate.class);
        doThrow(new CertificateExpiredException("expired")).when(certificate).checkValidity();
        lenient().when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(GENERAL_NAME_DNS, "*.example.org")));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, certificate));

        assertThat(connectorCertificateCheck.isReadyFor(HOST)).isFalse();
    }

    @ParameterizedTest(name = "[{index}] SAN {0} is not ready for host {1}")
    @CsvSource({
            "*.example.org, example.org",
            "*.example.org, a.issuer.example.org",
            "*.example.org, issuer.example.com",
            "*.org, example.org",
            "iss*.example.org, issuer.example.org"
    })
    void isNotReadyWhenTheWildcardDoesNotOverMatch(String subjectAlternativeName, String host) throws CertificateParsingException {
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(GENERAL_NAME_DNS, subjectAlternativeName)));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, certificate));

        assertThat(connectorCertificateCheck.isReadyFor(host)).isFalse();
    }
}

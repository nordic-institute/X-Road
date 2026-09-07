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
package org.niis.xroad.cs.admin.application.dstls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.dto.DsTlsCertificationAuthority;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificationAuthoritiesService;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProperties;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CentralServerDsTlsAcmeHostContextTest {

    @Mock
    private DataspaceIssuerProperties dataspaceIssuerProperties;
    @Mock
    private DsTlsCertificationAuthoritiesService dsTlsCertificationAuthoritiesService;

    private CentralServerDsTlsAcmeHostContext hostContext;

    @BeforeEach
    void setUp() {
        hostContext = new CentralServerDsTlsAcmeHostContext(dataspaceIssuerProperties, dsTlsCertificationAuthoritiesService);
    }

    @Test
    void getPublicHostnameShouldReturnTheConfiguredIssuerHost() {
        when(dataspaceIssuerProperties.getHost()).thenReturn("ds.example.org");

        assertThat(hostContext.getPublicHostname()).isEqualTo("ds.example.org");
    }

    @Test
    void getPublicHostnameShouldReturnNullWhenBlank() {
        when(dataspaceIssuerProperties.getHost()).thenReturn("   ");

        assertThat(hostContext.getPublicHostname()).isNull();
    }

    @Test
    void getEabAliasShouldBeTheFixedDsTlsAlias() {
        assertThat(hostContext.getEabAlias()).isEqualTo(CentralServerDsTlsAcmeHostContext.DS_TLS_ACME_ALIAS)
                .isEqualTo("dataspace-tls");
    }

    @Test
    void getAccountContactsShouldBeEmptyByDefault() {
        assertThat(hostContext.getAccountContacts()).isEmpty();
    }

    @Test
    void getDsTlsCertificationAuthoritiesShouldReadFromTheCsOwnServiceNotGlobalconf() {
        DsTlsCertificationAuthority ca = new DsTlsCertificationAuthority()
                .setName("Test DS TLS CA")
                .setAcmeServerDirectoryUrl("http://testca:8887")
                .setDsTlsCertificateProfileId("ds-tls-profile");
        when(dsTlsCertificationAuthoritiesService.findAll()).thenReturn(List.of(ca));

        List<ApprovedDsTlsCaInfo> result = hostContext.getDsTlsCertificationAuthorities();

        assertThat(result).hasSize(1);
        ApprovedDsTlsCaInfo caInfo = result.getFirst();
        assertThat(caInfo.getName()).isEqualTo("Test DS TLS CA");
        assertThat(caInfo.getAcmeServerDirectoryUrl()).isEqualTo("http://testca:8887");
        assertThat(caInfo.getDsTlsCertificateProfileId()).isEqualTo("ds-tls-profile");
    }

    @Test
    void getDsTlsCertificationAuthoritiesShouldReturnEmptyWhenNoneDesignated() {
        when(dsTlsCertificationAuthoritiesService.findAll()).thenReturn(List.of());

        assertThat(hostContext.getDsTlsCertificationAuthorities()).isEmpty();
    }

    @Test
    void notifyEnrollmentSuccessShouldNotThrow() {
        hostContext.notifyEnrollmentSuccess("ds.example.org", true);
    }

    @Test
    void notifyEnrollmentFailureShouldNotThrow() {
        hostContext.notifyEnrollmentFailure("ds.example.org", "boom");
    }
}

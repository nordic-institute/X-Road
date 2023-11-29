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
package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.AnchorUrl;
import org.niis.xroad.cs.admin.api.domain.AnchorUrlCert;
import org.niis.xroad.cs.admin.api.domain.TrustedAnchor;
import org.niis.xroad.cs.admin.api.service.ManagementServiceTlsCertificateService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TrustedAnchorService;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateParametersLoaderTest {
    public static final String ANCHOR_URL = "http://anchor.url";
    public static final byte[] ANCHOR_URL_CERT = "anchor-url-cert".getBytes(UTF_8);
    public static final String AUTH_REG_CERT_URL = "http://auth-reg-cert.url";
    public static final ClientId.Conf MANAGEMENT_REQUEST_SERVICE_PROVIDER_ID = ClientId.Conf.create("XRD", "CLASS", "M", "SUB");
    public static final X509Certificate AUTH_CERT_REG_SERVICE_CERT = TestCertUtil.getInternalKey().certChain[0];
    public static final int TIMESTAMPING_INTERVAL_SECONDS = 333;
    @Mock
    SystemParameterService systemParameterService;
    @Mock
    TrustedAnchorService trustedAnchorService;
    @Mock
    ManagementServiceTlsCertificateService managementServiceTlsCertificateService;

    @InjectMocks
    PrivateParametersLoader privateParametersLoader;

    @Test
    void shouldLoadParameters() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn("XRD");
        when(systemParameterService.getAuthCertRegUrl()).thenReturn(AUTH_REG_CERT_URL);
        when(systemParameterService.getManagementServiceProviderId()).thenReturn(MANAGEMENT_REQUEST_SERVICE_PROVIDER_ID);
        when(systemParameterService.getTimeStampingIntervalSeconds()).thenReturn(TIMESTAMPING_INTERVAL_SECONDS);
        when(managementServiceTlsCertificateService.getTlsCertificate()).thenReturn(AUTH_CERT_REG_SERVICE_CERT);
        when(trustedAnchorService.findAll()).thenReturn(List.of(createTrustedAnchor()));

        var parameters = privateParametersLoader.load();

        assertThat(parameters.getInstanceIdentifier()).isEqualTo("XRD");
        assertThat(parameters.getConfigurationAnchors()).singleElement().satisfies(configurationAnchor -> {
            assertThat(configurationAnchor.getInstanceIdentifier()).isEqualTo("INSTANCE");
            assertThat(configurationAnchor.getGeneratedAt()).isEqualTo(Instant.EPOCH);
            assertThat(configurationAnchor.getSources()).singleElement().satisfies(
                    source -> {
                        assertThat(source.getDownloadURL()).isEqualTo(ANCHOR_URL);
                        assertThat(source.getVerificationCerts())
                                .singleElement()
                                .isEqualTo(ANCHOR_URL_CERT);
                    }
            );
        });
        assertThat(parameters.getManagementService()).satisfies(managementService -> {
            assertThat(managementService.getAuthCertRegServiceAddress()).isEqualTo(AUTH_REG_CERT_URL);
            assertThat(managementService.getManagementRequestServiceProviderId()).isEqualTo(MANAGEMENT_REQUEST_SERVICE_PROVIDER_ID);
            assertThat(managementService.getAuthCertRegServiceCert()).isEqualTo(AUTH_CERT_REG_SERVICE_CERT.getEncoded());
        });
        assertThat(parameters.getTimeStampingIntervalSeconds()).isEqualTo(TIMESTAMPING_INTERVAL_SECONDS);
    }

    @Test
    void shouldThrowExceptionWhenAuthCertRegUrlIsBlank() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn("XRD");
        when(systemParameterService.getAuthCertRegUrl()).thenReturn("");

        assertThrows(ConfGeneratorException.class, () -> privateParametersLoader.load());
    }

    private static TrustedAnchor createTrustedAnchor() {
        var anchor = new TrustedAnchor();
        anchor.setInstanceIdentifier("INSTANCE");
        anchor.setGeneratedAt(Instant.EPOCH);
        anchor.setAnchorUrls(Set.of(createAnchorUrl()));
        return anchor;
    }

    private static AnchorUrl createAnchorUrl() {
        var anchorUrl = new AnchorUrl();
        anchorUrl.setUrl(ANCHOR_URL);
        anchorUrl.setAnchorUrlCerts(Set.of(createAnchorUrlCert()));
        return anchorUrl;
    }

    private static AnchorUrlCert createAnchorUrlCert() {
        var anchorUrlCert = new AnchorUrlCert();
        anchorUrlCert.setCert(ANCHOR_URL_CERT);
        return anchorUrlCert;
    }
}

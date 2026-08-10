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
package org.niis.xroad.proxy.core.healthcheck.readiness;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.proxy.core.configuration.ProxyHealthCheckProperties;

import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthKeyOcspReadinessCheckTest {

    private static final String CHECK_NAME = "PROXY_AUTH_KEY_OCSP_READINESS_CHECK";

    @Mock
    private KeyConfProvider keyConfProvider;

    private AuthKeyOcspReadinessCheck check;

    @BeforeEach
    void setUp() {
        var xRoadConfig = XRoadConfigBuilder.create().register(ProxyConfigKeys.instance()).build();
        ProxyHealthCheckProperties props = new ProxyHealthCheckProperties(xRoadConfig);
        this.check = new AuthKeyOcspReadinessCheck(keyConfProvider, props);
    }

    @Test
    void returnsUpWhenAuthKeyIsNull() {
        when(keyConfProvider.getAuthKey()).thenReturn(null);

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get()).containsEntry("status", "AWAITING_AUTH_KEY");
    }

    @Test
    void returnsUpWhenCertChainIsNull() {
        AuthKey authKey = new AuthKey(null, null);
        when(keyConfProvider.getAuthKey()).thenReturn(authKey);

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getData().get()).containsEntry("status", "AWAITING_CERT_CHAIN");
    }

    @Test
    void returnsUpWhenEndEntityCertIsNull() {
        CertChain certChain = mock(CertChain.class);
        when(certChain.getEndEntityCert()).thenReturn(null);
        AuthKey authKey = new AuthKey(certChain, null);
        when(keyConfProvider.getAuthKey()).thenReturn(authKey);

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getData().get()).containsEntry("status", "AWAITING_END_ENTITY_CERT");
    }

    @Test
    void returnsDownWhenOcspLookupThrows() throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        CertChain certChain = mock(CertChain.class);
        when(certChain.getEndEntityCert()).thenReturn(cert);
        AuthKey authKey = new AuthKey(certChain, null);
        when(keyConfProvider.getAuthKey()).thenReturn(authKey);
        when(keyConfProvider.getOcspResponse(any(X509Certificate.class)))
                .thenThrow(new RuntimeException("boom"));

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getData().get())
                .containsEntry("status", "OCSP_LOOKUP_FAILED")
                .containsEntry("error", "RuntimeException");
    }

    @Test
    void returnsDownWhenOcspStatusNotSuccessful() throws Exception {
        mockOcspStatus(OCSPResp.MALFORMED_REQUEST);

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getData().get())
                .containsEntry("status", "OCSP_FAILED")
                .containsEntry("ocsp_status", (long) OCSPResp.MALFORMED_REQUEST);
    }

    @Test
    void returnsUpWhenOcspStatusSuccessful() throws Exception {
        mockOcspStatus(OCSPResp.SUCCESSFUL);

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getData().get())
                .containsEntry("status", "OK")
                .containsEntry("ocsp_status", (long) OCSPResp.SUCCESSFUL);
    }

    private void mockOcspStatus(int status) throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        CertChain certChain = mock(CertChain.class);
        when(certChain.getEndEntityCert()).thenReturn(cert);
        AuthKey authKey = new AuthKey(certChain, null);
        when(keyConfProvider.getAuthKey()).thenReturn(authKey);

        OCSPResp resp = mock(OCSPResp.class);
        when(resp.getStatus()).thenReturn(status);
        when(keyConfProvider.getOcspResponse(any(X509Certificate.class))).thenReturn(resp);
    }
}

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
package org.niis.xroad.restapi.openapi;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.vault.DsTlsEnrollmentMethod;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.restapi.config.AllowedHostnamesConfig;
import org.niis.xroad.restapi.config.UserAuthenticationConfig;
import org.niis.xroad.restapi.config.UserRoleConfig;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.niis.xroad.restapi.test.AbstractSpringMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test DsTlsCertificateController's DataSpace TLS certificate enrollment status endpoint. Reachable from both
 * Security Server's and Central Server's admin APIs, since the controller lives in common-admin-api.
 */
class DsTlsCertificateControllerTest extends AbstractSpringMvcTest {

    private static final String ENROLLMENT_STATUS_PATH = ControllerUtil.API_V1_PREFIX + "/ds-tls-certificate/enrollment-status";

    @MockitoBean
    private DsTlsCertificateService dsTlsCertificateService;
    @MockitoBean
    private AllowedHostnamesConfig allowedHostnamesConfig;
    @MockitoBean
    private UserRoleConfig userRoleConfig;
    @MockitoBean
    private UserAuthenticationConfig userAuthenticationConfig;

    @Test
    @WithMockUser(authorities = "VIEW_DS_TLS_CERT")
    void getDsTlsCertificateEnrollmentStatusManual() throws Exception {
        Instant nextRenewalTime = Instant.now().plusSeconds(3600);
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.MANUAL, nextRenewalTime, null));

        mockMvc.perform(get(ENROLLMENT_STATUS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollment_method").value("MANUAL"))
                .andExpect(jsonPath("$.last_error").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "VIEW_DS_TLS_CERT")
    void getDsTlsCertificateEnrollmentStatusAcmeWithError() throws Exception {
        Instant nextRenewalTime = Instant.now().plusSeconds(60);
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewalTime, "order failed"));

        mockMvc.perform(get(ENROLLMENT_STATUS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollment_method").value("ACME"))
                .andExpect(jsonPath("$.last_error").value("order failed"));
    }

    @Test
    @WithMockUser(authorities = "VIEW_DS_TLS_CERT")
    void getDsTlsCertificateEnrollmentStatusNoneConfigured() throws Exception {
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(null, null, null));

        mockMvc.perform(get(ENROLLMENT_STATUS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollment_method").value("NONE"))
                .andExpect(jsonPath("$.next_renewal_time").doesNotExist())
                .andExpect(jsonPath("$.last_error").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "VIEW_DS_TLS_CERT")
    void getDsTlsCertificateEnrollmentStatusNoneConfiguredWithStuckError() throws Exception {
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(null, null, "first enrollment attempt failed"));

        mockMvc.perform(get(ENROLLMENT_STATUS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollment_method").value("NONE"))
                .andExpect(jsonPath("$.last_error").value("first enrollment attempt failed"));
    }

    @Test
    @WithMockUser(authorities = "VIEW_DATASPACE_STATUS")
    void getDsTlsCertificateEnrollmentStatusWithoutRequiredAuthority() throws Exception {
        mockMvc.perform(get(ENROLLMENT_STATUS_PATH))
                .andExpect(status().isForbidden());
    }
}

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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.DsTlsEnrollmentMethod;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.restapi.config.AllowedHostnamesConfig;
import org.niis.xroad.restapi.config.UserAuthenticationConfig;
import org.niis.xroad.restapi.config.UserRoleConfig;
import org.niis.xroad.restapi.dstls.DsTlsAcmeAvailability;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.niis.xroad.restapi.test.AbstractSpringMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test DsTlsCertificateController's DataSpace TLS certificate enrollment status and ACME order endpoints.
 * Reachable from both Security Server's and Central Server's admin APIs, since the controller lives in
 * common-admin-api.
 */
class DsTlsCertificateControllerTest extends AbstractSpringMvcTest {

    private static final String ENROLLMENT_STATUS_PATH = ControllerUtil.API_V1_PREFIX + "/ds-tls-certificate/enrollment-status";
    private static final String ACME_ORDER_PATH = ControllerUtil.API_V1_PREFIX + "/ds-tls-certificate/acme-order";
    private static final DsTlsAcmeAvailability NOT_AVAILABLE = new DsTlsAcmeAvailability(false, List.of(), null);
    private static final int RSA_KEY_LENGTH = 2048;

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
        when(dsTlsCertificateService.getAcmeAvailability()).thenReturn(NOT_AVAILABLE);

        mockMvc.perform(get(ENROLLMENT_STATUS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollment_method").value("MANUAL"))
                .andExpect(jsonPath("$.last_error").doesNotExist())
                .andExpect(jsonPath("$.acme_available").value(false));
    }

    @Test
    @WithMockUser(authorities = "VIEW_DS_TLS_CERT")
    void getDsTlsCertificateEnrollmentStatusAcmeWithError() throws Exception {
        Instant nextRenewalTime = Instant.now().plusSeconds(60);
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewalTime, "order failed"));
        when(dsTlsCertificateService.getAcmeAvailability()).thenReturn(NOT_AVAILABLE);

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
        when(dsTlsCertificateService.getAcmeAvailability()).thenReturn(NOT_AVAILABLE);

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
        when(dsTlsCertificateService.getAcmeAvailability()).thenReturn(NOT_AVAILABLE);

        mockMvc.perform(get(ENROLLMENT_STATUS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollment_method").value("NONE"))
                .andExpect(jsonPath("$.last_error").value("first enrollment attempt failed"));
    }

    @Test
    @WithMockUser(authorities = "VIEW_DS_TLS_CERT")
    void getDsTlsCertificateEnrollmentStatusReportsAcmeAvailabilityAndCasAndHostname() throws Exception {
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(null, null, null));
        when(dsTlsCertificateService.getAcmeAvailability())
                .thenReturn(new DsTlsAcmeAvailability(true, List.of("Test CA", "Other CA"), "ss.example.org"));

        mockMvc.perform(get(ENROLLMENT_STATUS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acme_available").value(true))
                .andExpect(jsonPath("$.acme_cas[0].name").value("Test CA"))
                .andExpect(jsonPath("$.acme_cas[1].name").value("Other CA"))
                .andExpect(jsonPath("$.public_hostname").value("ss.example.org"));
    }

    @Test
    @WithMockUser(authorities = "VIEW_DATASPACE_STATUS")
    void getDsTlsCertificateEnrollmentStatusWithoutRequiredAuthority() throws Exception {
        mockMvc.perform(get(ENROLLMENT_STATUS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ORDER_DS_TLS_CERT")
    void orderDsTlsCertificateShouldReturnTheIssuedCertificate() throws Exception {
        when(dsTlsCertificateService.orderCertificate("Test CA", "CN=ds.example.org", "ds.example.org"))
                .thenReturn(selfSignedCertificate());

        mockMvc.perform(post(ACME_ORDER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ca_name":"Test CA","distinguished_name":"CN=ds.example.org","subject_alt_name":"ds.example.org"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hash").exists());
    }

    @Test
    @WithMockUser(authorities = "ORDER_DS_TLS_CERT")
    void orderDsTlsCertificateShouldReturnBadRequestForAnUnknownCa() throws Exception {
        when(dsTlsCertificateService.orderCertificate(any(), any(), any()))
                .thenThrow(new BadRequestException(ErrorCode.DS_TLS_CA_NOT_FOUND.build("Unknown CA")));

        mockMvc.perform(post(ACME_ORDER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ca_name":"Unknown CA","distinguished_name":"CN=ds.example.org","subject_alt_name":"ds.example.org"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ORDER_DS_TLS_CERT")
    void orderDsTlsCertificateShouldReturnNotFoundWhenNoKeyGenerated() throws Exception {
        when(dsTlsCertificateService.orderCertificate(any(), any(), any()))
                .thenThrow(new NotFoundException(ErrorCode.DS_TLS_KEY_NOT_GENERATED.build()));

        mockMvc.perform(post(ACME_ORDER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ca_name":"Test CA","distinguished_name":"CN=ds.example.org","subject_alt_name":"ds.example.org"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ORDER_DS_TLS_CERT")
    void orderDsTlsCertificateShouldReturnBadRequestForAnInvalidDistinguishedName() throws Exception {
        when(dsTlsCertificateService.orderCertificate(any(), any(), any()))
                .thenThrow(new BadRequestException(ErrorCode.INVALID_DISTINGUISHED_NAME.build()));

        mockMvc.perform(post(ACME_ORDER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ca_name":"Test CA","distinguished_name":"not a dn","subject_alt_name":"ds.example.org"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ORDER_DS_TLS_CERT")
    void orderDsTlsCertificateShouldReturnBadRequestForABlankSubjectAltName() throws Exception {
        when(dsTlsCertificateService.orderCertificate(any(), any(), any()))
                .thenThrow(new BadRequestException(ErrorCode.DS_TLS_INVALID_SUBJECT_ALT_NAME.build()));

        mockMvc.perform(post(ACME_ORDER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ca_name":"Test CA","distinguished_name":"CN=ds.example.org","subject_alt_name":" "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "VIEW_DS_TLS_CERT")
    void orderDsTlsCertificateWithoutRequiredAuthority() throws Exception {
        mockMvc.perform(post(ACME_ORDER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ca_name":"Test CA","distinguished_name":"CN=ds.example.org","subject_alt_name":"ds.example.org"}
                                """))
                .andExpect(status().isForbidden());
    }

    private static X509Certificate selfSignedCertificate() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(RSA_KEY_LENGTH);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        var subject = new X500Name("CN=ds.example.org");
        var certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
    }
}

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
package org.niis.xroad.securityserver.restapi.openapi;

import org.junit.Test;
import org.niis.xroad.common.vault.DsTlsEnrollmentMethod;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceTlsCertificateEnrollmentStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceTlsCertificateEnrollmentStatusDto.EnrollmentMethodEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

public class DataspaceApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    DataspaceApiController dataspaceApiController;
    @MockitoBean
    private DsTlsCertificateService dsTlsCertificateService;

    @Test
    @WithMockUser(authorities = {"VIEW_DS_TLS_CERT"})
    public void getDataspaceTlsCertificateEnrollmentStatusManual() {
        Instant nextRenewalTime = Instant.now().plusSeconds(3600);
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.MANUAL, nextRenewalTime, null));

        ResponseEntity<DataspaceTlsCertificateEnrollmentStatusDto> response =
                dataspaceApiController.getDataspaceTlsCertificateEnrollmentStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DataspaceTlsCertificateEnrollmentStatusDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getEnrollmentMethod()).isEqualTo(EnrollmentMethodEnum.MANUAL);
        assertThat(body.getNextRenewalTime()).isEqualTo(nextRenewalTime.atOffset(ZoneOffset.UTC));
        assertThat(body.getLastError()).isNull();
    }

    @Test
    @WithMockUser(authorities = {"VIEW_DS_TLS_CERT"})
    public void getDataspaceTlsCertificateEnrollmentStatusAcmeWithError() {
        Instant nextRenewalTime = Instant.now().plusSeconds(60);
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewalTime, "order failed"));

        ResponseEntity<DataspaceTlsCertificateEnrollmentStatusDto> response =
                dataspaceApiController.getDataspaceTlsCertificateEnrollmentStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DataspaceTlsCertificateEnrollmentStatusDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getEnrollmentMethod()).isEqualTo(EnrollmentMethodEnum.ACME);
        assertThat(body.getNextRenewalTime()).isEqualTo(nextRenewalTime.atOffset(ZoneOffset.UTC));
        assertThat(body.getLastError()).isEqualTo("order failed");
    }

    @Test
    @WithMockUser(authorities = {"VIEW_DS_TLS_CERT"})
    public void getDataspaceTlsCertificateEnrollmentStatusNoneConfigured() {
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(null, null, null));

        ResponseEntity<DataspaceTlsCertificateEnrollmentStatusDto> response =
                dataspaceApiController.getDataspaceTlsCertificateEnrollmentStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DataspaceTlsCertificateEnrollmentStatusDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getEnrollmentMethod()).isEqualTo(EnrollmentMethodEnum.NONE);
        assertThat(body.getNextRenewalTime()).isNull();
        assertThat(body.getLastError()).isNull();
    }

    @Test
    @WithMockUser(authorities = {"VIEW_DS_TLS_CERT"})
    public void getDataspaceTlsCertificateEnrollmentStatusNoneConfiguredWithStuckError() {
        when(dsTlsCertificateService.getEnrollmentStatus())
                .thenReturn(new DsTlsEnrollmentStatus(null, null, "first enrollment attempt failed"));

        ResponseEntity<DataspaceTlsCertificateEnrollmentStatusDto> response =
                dataspaceApiController.getDataspaceTlsCertificateEnrollmentStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DataspaceTlsCertificateEnrollmentStatusDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getEnrollmentMethod()).isEqualTo(EnrollmentMethodEnum.NONE);
        assertThat(body.getNextRenewalTime()).isNull();
        assertThat(body.getLastError()).isEqualTo("first enrollment attempt failed");
    }

    @Test
    @WithMockUser(authorities = {"VIEW_DATASPACE_STATUS"})
    public void getDataspaceTlsCertificateEnrollmentStatusWithoutRequiredAuthority() {
        assertThatThrownBy(() -> dataspaceApiController.getDataspaceTlsCertificateEnrollmentStatus())
                .isInstanceOf(AccessDeniedException.class);
    }
}

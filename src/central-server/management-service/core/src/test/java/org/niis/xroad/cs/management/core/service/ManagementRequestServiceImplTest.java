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
package org.niis.xroad.cs.management.core.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.request.AuthCertDeletionRequestType;
import ee.ria.xroad.common.request.ClientRequestType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.client.FeignManagementRequestsApi;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_ENABLE_REQUEST;

@ExtendWith(MockitoExtension.class)
class ManagementRequestServiceImplTest {
    private static final Integer REQUEST_ID = 123;

    @Mock
    private FeignManagementRequestsApi managementRequestsApi;
    @Mock
    private SecurityServerIdConverter securityServerIdConverter;
    @Mock
    private ClientIdConverter clientIdConverter;

    @InjectMocks
    private ManagementRequestServiceImpl managementRequestService;

    @Test
    void shouldAddManagementRequestSucceed() {
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.accepted().body(new ManagementRequestDto().id(REQUEST_ID)));

        ClientRequestType request = new ClientRequestType();

        Integer result = managementRequestService.addManagementRequest(request, CLIENT_ENABLE_REQUEST);

        assertThat(result).isEqualTo(REQUEST_ID);
    }

    @Test
    void shouldAddManagementCertDeletionRequestSucceed() {
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.accepted().body(new ManagementRequestDto().id(REQUEST_ID)));

        AuthCertDeletionRequestType request = new AuthCertDeletionRequestType();

        Integer result = managementRequestService.addManagementRequest(request);

        assertThat(result).isEqualTo(REQUEST_ID);
    }

    @Test
    void shouldAddManagementRequestThrownCodedException() {
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.accepted().build());

        ClientRequestType request = new ClientRequestType();

        assertThatExceptionOfType(CodedException.class)
                .isThrownBy(() -> managementRequestService.addManagementRequest(request, CLIENT_ENABLE_REQUEST));
    }

    @Test
    void shouldAddManagementCertDeletionRequestThrownCodedException() {
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.accepted().build());

        AuthCertDeletionRequestType request = new AuthCertDeletionRequestType();

        assertThatExceptionOfType(CodedException.class)
                .isThrownBy(() -> managementRequestService.addManagementRequest(request));
    }
}

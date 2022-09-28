/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.junit.helper.WithInOrder;

import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.dto.ManagementRequestInfoDto;
import org.niis.xroad.centralserver.restapi.repository.ManagementRequestViewRepository;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.centralserver.restapi.service.managementrequest.ManagementRequestService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestType.CLIENT_REGISTRATION_REQUEST;

@ExtendWith(MockitoExtension.class)
public class SecurityServerServiceTest implements WithInOrder {

    @Mock
    private ManagementRequestService managementRequestService;

    @Mock
    private ManagementRequestInfoDto managementRequestInfoDto;

    @Mock
    private SecurityServerId serverId;

    @InjectMocks
    private SecurityServerService securityServerService;


    @Nested
    @DisplayName("findSecurityServerRegistrationStatus(SecurityServerId serverId)")
    public class SecurityServerRegStatusMethod implements WithInOrder {

        @Test
        @DisplayName("should find status successfully")
        public void shouldAddSubsystemSuccessfully() {
            Page<ManagementRequestInfoDto> requestInfoDtos = new PageImpl<>(List.of(managementRequestInfoDto));
            doReturn(requestInfoDtos).when(managementRequestService)
                    .findRequests(ManagementRequestViewRepository.Criteria.builder()
                            .origin(Origin.SECURITY_SERVER)
                            .serverId(serverId)
                            .types(List.of(CLIENT_REGISTRATION_REQUEST))
                            .build(), Pageable.unpaged());
            doReturn(ManagementRequestStatus.APPROVED).when(managementRequestInfoDto).getStatus();

            ManagementRequestStatus result = securityServerService.findSecurityServerRegistrationStatus(serverId);

            assertNotNull(result);
            assertEquals(ManagementRequestStatus.APPROVED, result);
        }

        @Test
        @DisplayName("should find status successfully")
        public void shouldThrowNotFound() {
            NotFoundException expectedThrows = mock(NotFoundException.class);
            Page<ManagementRequestInfoDto> requestInfoDtos = Page.empty();
            doReturn(requestInfoDtos).when(managementRequestService)
                    .findRequests(ManagementRequestViewRepository.Criteria.builder()
                            .origin(Origin.SECURITY_SERVER)
                            .serverId(serverId)
                            .types(List.of(CLIENT_REGISTRATION_REQUEST))
                            .build(), Pageable.unpaged());

            Executable testable = () -> securityServerService.findSecurityServerRegistrationStatus(serverId);

            NotFoundException actualThrows = assertThrows(NotFoundException.class, testable);
            assertEquals(expectedThrows, actualThrows);
        }
    }
}

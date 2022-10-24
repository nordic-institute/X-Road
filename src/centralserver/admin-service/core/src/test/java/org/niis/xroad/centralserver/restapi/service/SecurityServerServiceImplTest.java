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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.service.managementrequest.ManagementRequestServiceImpl;
import org.niis.xroad.cs.admin.api.dto.ManagementRequestInfoDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class SecurityServerServiceImplTest implements WithInOrder {

    @Mock
    private ManagementRequestServiceImpl managementRequestService;

    @Mock
    private ManagementRequestInfoDto managementRequestInfoDto;

    @Mock
    private SecurityServerId serverId;

    @InjectMocks
    private SecurityServerServiceImpl securityServerService;


    @Nested
    @DisplayName("findSecurityServerRegistrationStatus(SecurityServerId serverId)")
    public class SecurityServerRegStatusMethod implements WithInOrder {

        @Test
        @DisplayName("should find management status approved")
        public void shouldReturnStatusApproved() {
            Page<ManagementRequestInfoDto> requestInfoDtos = new PageImpl<>(List.of(managementRequestInfoDto));
            doReturn(requestInfoDtos).when(managementRequestService)
                    .findRequests(any(), any());
            doReturn(ManagementRequestStatus.APPROVED).when(managementRequestInfoDto).getStatus();

            ManagementRequestStatus result = securityServerService.findSecurityServerRegistrationStatus(serverId);

            assertNotNull(result);
            assertEquals(ManagementRequestStatus.APPROVED, result);
        }

        @Test
        @DisplayName("should return null if no management request exit")
        public void shouldReturnNullWhenRequestNotFound() {
            Page<ManagementRequestInfoDto> emptyRequestInfoDtos = Page.empty();
            doReturn(emptyRequestInfoDtos).when(managementRequestService)
                    .findRequests(any(), any());

            ManagementRequestStatus result = securityServerService.findSecurityServerRegistrationStatus(serverId);

            assertNull(result);
        }
    }
}

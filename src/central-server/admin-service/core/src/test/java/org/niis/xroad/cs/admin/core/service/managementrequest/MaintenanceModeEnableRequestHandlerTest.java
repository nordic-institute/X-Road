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

package org.niis.xroad.cs.admin.core.service.managementrequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.MaintenanceModeEnableRequest;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.core.entity.MaintenanceModeEnableRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;

@ExtendWith(MockitoExtension.class)
class MaintenanceModeEnableRequestHandlerTest {
    private static final String MESSAGE = "I'll be back";

    @Mock
    private IdentifierRepository<SecurityServerIdEntity> serverIds;
    @Mock
    private SecurityServerRepository servers;
    @Mock
    private RequestRepository<MaintenanceModeEnableRequestEntity> maintenanceModeEnableRequests;
    @Mock
    private RequestMapper requestMapper;

    private final SecurityServerId securityServerId = SecurityServerId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SERVER-CODE");

    @InjectMocks
    private MaintenanceModeEnableRequestHandler handler;

    @Captor
    private ArgumentCaptor<MaintenanceModeEnableRequestEntity> argumentCaptor;

    @Test
    void canAutoApprove() {
        var request = new MaintenanceModeEnableRequest(SECURITY_SERVER, securityServerId, MESSAGE);
        assertThat(handler.canAutoApprove(request)).isFalse();
    }

    @Test
    void add() {
        var request = new MaintenanceModeEnableRequest(SECURITY_SERVER, securityServerId, MESSAGE);

        var securityServerEntity = mock(SecurityServerEntity.class);
        var securityServerIdEntity = mock(SecurityServerIdEntity.class);
        var requestEntity = mock(MaintenanceModeEnableRequestEntity.class);

        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(securityServerIdEntity);
        when(servers.findBy(securityServerIdEntity)).thenReturn(Optional.of(securityServerEntity));
        when(securityServerEntity.isInMaintenanceMode()).thenReturn(false);
        when(maintenanceModeEnableRequests.save(argumentCaptor.capture())).thenReturn(requestEntity);

        handler.add(request);

        verify(securityServerEntity).setInMaintenanceMode(true);
        verify(securityServerEntity).setMaintenanceModeMessage(MESSAGE);
        verify(servers).save(securityServerEntity);
        assertThat(argumentCaptor.getValue().getComments()).isEqualTo(MESSAGE);
        verify(requestMapper).toDto(requestEntity);
    }

    @Test
    void alreadyInMaintenanceMode() {
        var request = new MaintenanceModeEnableRequest(SECURITY_SERVER, securityServerId, MESSAGE);

        var securityServerEntity = mock(SecurityServerEntity.class);
        var securityServerIdEntity = mock(SecurityServerIdEntity.class);

        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(securityServerIdEntity);
        when(servers.findBy(securityServerIdEntity)).thenReturn(Optional.of(securityServerEntity));
        when(securityServerEntity.isInMaintenanceMode()).thenReturn(true);

        assertThatThrownBy(() -> handler.add(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Error[code=management_request_server_already_in_maintenance_mode, metadata=[%s]]".formatted(securityServerId));
    }


    @Test
    void securityServerNotFound() {
        var request = new MaintenanceModeEnableRequest(SECURITY_SERVER, securityServerId, MESSAGE);

        var securityServerIdEntity = mock(SecurityServerIdEntity.class);

        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(securityServerIdEntity);
        when(servers.findBy(securityServerIdEntity)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.add(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=management_request_server_not_found, metadata=[%s]]".formatted(securityServerId));
    }

}

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
package org.niis.xroad.cs.admin.rest.api.converter.db;

import ee.ria.xroad.common.junit.helper.WithInOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.ServerClient;
import org.niis.xroad.cs.admin.api.domain.Subsystem;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.rest.api.converter.AbstractDtoConverterTest;
import org.niis.xroad.cs.openapi.model.ClientIdDto;
import org.niis.xroad.cs.openapi.model.SubsystemDto;
import org.niis.xroad.cs.openapi.model.UsedSecurityServersDto;

import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SubsystemDtoConverterTest extends AbstractDtoConverterTest implements WithInOrder {

    @Mock
    private Subsystem subsystem;
    @Mock
    private SubsystemDto subsystemDto;

    @Mock
    private ServerClient serverClient;

    @Mock
    private SecurityServerService securityServerService;

    @Mock
    private SubsystemService subsystemService;

    @Mock
    private ClientIdDtoConverter clientIdDtoConverter;

    @InjectMocks
    private SubsystemDtoConverter converter;

    @Nested
    @DisplayName("toDto(Subsystem source)")
    public class ToDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should check for sanity")
        public void shouldCheckForSanity() {
            Set<ServerClient> serverClients = Set.of(serverClient);
            SubsystemId clientId = SubsystemId.create(INSTANCE_ID, MEMBER_CLASS_CODE,
                    MEMBER_CODE, SUBSYSTEM_CODE);
            ClientIdDto clientIdDto = new ClientIdDto().subsystemCode(SUBSYSTEM_CODE);
            doReturn(clientId).when(subsystem).getIdentifier();
            doReturn(clientIdDto).when(clientIdDtoConverter).toDto(clientId);
            doReturn(serverClients).when(subsystem).getServerClients();

            when(serverClient.getServerOwner()).thenReturn(MEMBER_NAME);
            when(serverClient.getServerCode()).thenReturn(SERVER_CODE);

            doReturn(ManagementRequestStatus.APPROVED).when(securityServerService)
                    .findSecurityServerClientRegistrationStatus(any(), any());

            SubsystemDto converted = converter.toDto(subsystem);

            assertNotNull(converted);
            assertEquals(SUBSYSTEM_CODE, converted.getSubsystemId().getSubsystemCode());
            assertEquals(1, converted.getUsedSecurityServers().size());
            UsedSecurityServersDto convertedServerClient = converted.getUsedSecurityServers().get(0);
            assertEquals(SERVER_CODE, convertedServerClient.getServerCode());
            assertEquals(MEMBER_NAME, convertedServerClient.getServerOwner());
        }
    }

    @Nested
    @DisplayName("fromDto(SubsystemDto source)")
    public class FromDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should use persisted entity if present")
        public void shouldUsePersistedEntityIfPresent() {
            ClientIdDto clientIdDto = new ClientIdDto();
            SubsystemId clientId = SubsystemId.create(INSTANCE_ID, MEMBER_CLASS_CODE,
                    MEMBER_CODE, SUBSYSTEM_CODE);
            doReturn(clientIdDto).when(subsystemDto).getSubsystemId();
            doReturn(clientId).when(clientIdDtoConverter).fromDto(clientIdDto);

            when(subsystemService.findByIdentifier(any())).thenReturn(Optional.of(subsystem));

            Subsystem converted = converter.fromDto(subsystemDto);

            assertEquals(subsystem, converted);
        }
    }
}

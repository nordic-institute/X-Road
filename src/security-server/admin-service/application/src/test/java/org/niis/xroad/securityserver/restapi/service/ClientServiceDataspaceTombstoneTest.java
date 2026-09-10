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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.securityserver.restapi.cache.SubsystemNameStatus;
import org.niis.xroad.securityserver.restapi.repository.AccessRightRepository;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.repository.IdentifierRepository;
import org.niis.xroad.securityserver.restapi.repository.LocalGroupRepository;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.serverconf.model.Client.STATUS_SAVED;

/**
 * Covers only the dataspace participant binding tombstone behavior of local client deletion; the
 * pre-existing delete semantics (owner guard, status guard, identifier/access-right cleanup) are
 * covered by {@code ClientServiceIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class ClientServiceDataspaceTombstoneTest {

    private static final ClientId OWNER = ClientId.Conf.create("TEST", "ORG", "OWNER");

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private ServerConfService serverConfService;
    @Mock
    private IdentifierRepository identifierRepository;
    @Mock
    private LocalGroupRepository localGroupRepository;
    @Mock
    private AccessRightRepository accessRightRepository;
    @Mock
    private SubsystemNameStatus subsystemNameStatus;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private CatalogInvalidationNotifier catalogInvalidationNotifier;
    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private ServerConfEntity serverConfEntity;

    private ClientService clientService;
    private Set<ClientEntity> clients;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository, null, globalConfProvider, serverConfService, null,
                identifierRepository, localGroupRepository, accessRightRepository, null,
                new CurrentSecurityServerId(SecurityServerId.Conf.create(OWNER, "SS1")), subsystemNameStatus,
                auditDataHelper, catalogInvalidationNotifier, dsParticipantRepository,
                mock(CurrentSecurityServerSignCertificates.class));

        clients = new HashSet<>();
        lenient().when(serverConfService.getServerConfEntity()).thenReturn(serverConfEntity);
        lenient().when(serverConfEntity.getClients()).thenReturn(clients);
        lenient().when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());
        lenient().when(dsParticipantRepository.decommissionMember(any())).thenReturn(false);
        lenient().when(globalConfProvider.getClientSecurityServers(any())).thenReturn(Set.of());
    }

    @Test
    void flipsBindingWhenDeletedMemberHasNoClientsLeft() {
        ClientId member = ClientId.Conf.create("TEST", "ORG", "M1");
        ClientEntity memberClient = clientEntityFor(member);
        clients.add(memberClient);
        when(clientRepository.getClient(member)).thenReturn(memberClient);
        when(dsParticipantRepository.decommissionMember(member)).thenReturn(true);

        clientService.deleteLocalClient(member);

        verify(dsParticipantRepository).decommissionMember(member);
    }

    @Test
    void writesNothingWhenDeletedMemberHasNoBoundParticipant() {
        ClientId member = ClientId.Conf.create("TEST", "ORG", "M2");
        ClientEntity memberClient = clientEntityFor(member);
        clients.add(memberClient);
        when(clientRepository.getClient(member)).thenReturn(memberClient);

        assertDoesNotThrow(() -> clientService.deleteLocalClient(member));

        verify(dsParticipantRepository).decommissionMember(member);
    }

    @Test
    void doesNotDecommissionBindingWhenMemberHasARemainingSubsystem() {
        ClientId member = ClientId.Conf.create("TEST", "ORG", "M3");
        ClientId subsystem = ClientId.Conf.create("TEST", "ORG", "M3", "SUB");
        ClientEntity memberClient = clientEntityFor(member);
        ClientEntity subsystemClient = clientEntityFor(subsystem);
        clients.add(memberClient);
        clients.add(subsystemClient);
        when(clientRepository.getClient(member)).thenReturn(memberClient);

        clientService.deleteLocalClient(member);

        verify(dsParticipantRepository, never()).decommissionMember(any());
    }

    @Test
    void decommissionsBindingForBareMemberWhenLastRemainingClientIsASubsystem() {
        ClientId member = ClientId.Conf.create("TEST", "ORG", "M4");
        ClientId subsystem = ClientId.Conf.create("TEST", "ORG", "M4", "SUB");
        ClientEntity subsystemClient = clientEntityFor(subsystem);
        clients.add(subsystemClient);
        when(clientRepository.getClient(subsystem)).thenReturn(subsystemClient);
        when(dsParticipantRepository.decommissionMember(member)).thenReturn(true);

        clientService.deleteLocalClient(subsystem);

        verify(dsParticipantRepository).decommissionMember(member);
    }

    private ClientEntity clientEntityFor(ClientId id) {
        ClientEntity entity = mock(ClientEntity.class);
        lenient().when(entity.getIdentifier()).thenReturn(ClientIdEntityFactory.create(id));
        lenient().when(entity.getClientStatus()).thenReturn(STATUS_SAVED);
        return entity;
    }
}

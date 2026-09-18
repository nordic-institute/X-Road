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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceParticipantBindingServiceTest {

    private static final ClientId MEMBER = ClientId.Conf.create("TEST", "ORG", "MEMBER");
    private static final ClientId OTHER_MEMBER = ClientId.Conf.create("TEST", "ORG", "OTHER");
    private static final String SS_HOST = "ss.example.test:7183";

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private Dataspace dataspace;
    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private OwnSecurityServerResolver ownSecurityServerResolver;

    private DataspaceParticipantBindingService service;

    @BeforeEach
    void setUp() {
        lenient().when(dataspace.getIdentityHubDidPort()).thenReturn(7183);
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        lenient().when(ownSecurityServerResolver.registeredAddress()).thenReturn(Optional.of("ss.example.test"));

        service = new DataspaceParticipantBindingService(dsParticipantRepository,
                new DataspaceDidAuthority(ownSecurityServerResolver, adminServiceProperties));
    }

    @Test
    void bindsDerivedIdentifiersForEveryUnboundMember() {
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());

        assertThat(service.bindMembersIfAbsent(List.of(MEMBER, OTHER_MEMBER), true)).isEqualTo(2);

        verify(dsParticipantRepository).bindMemberParticipant(MEMBER,
                ParticipantIdentifierScheme.memberCtxId(MEMBER),
                ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST));
        verify(dsParticipantRepository).bindMemberParticipant(OTHER_MEMBER,
                ParticipantIdentifierScheme.memberCtxId(OTHER_MEMBER),
                ParticipantIdentifierScheme.memberDid(OTHER_MEMBER, SS_HOST));
    }

    @Test
    void leavesAlreadyBoundMembersUntouched() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER))
                .thenReturn(Optional.of(mock(DsParticipantEntity.class)));
        when(dsParticipantRepository.findByMemberIdentifier(OTHER_MEMBER)).thenReturn(Optional.empty());

        assertThat(service.bindMembersIfAbsent(List.of(MEMBER, OTHER_MEMBER), true)).isEqualTo(1);

        verify(dsParticipantRepository, never()).bindMemberParticipant(eq(MEMBER), anyString(), anyString());
        verify(dsParticipantRepository).bindMemberParticipant(eq(OTHER_MEMBER), anyString(), anyString());
    }

    @Test
    void writesNothingWhenEveryMemberIsAlreadyBound() {
        when(dsParticipantRepository.findByMemberIdentifier(any()))
                .thenReturn(Optional.of(mock(DsParticipantEntity.class)));

        assertThat(service.bindMembersIfAbsent(List.of(MEMBER), true)).isZero();

        verify(dsParticipantRepository, never()).bindMemberParticipant(any(), anyString(), anyString());
    }

    @Test
    void readsNoMemberStateBeforeTheServerHasARegisteredAuthenticationCertificate() {
        assertThat(service.bindMembersIfAbsent(List.of(MEMBER, OTHER_MEMBER), false)).isZero();

        verifyNoInteractions(dsParticipantRepository);
    }

    @Test
    void keepsBindingTheRemainingMembersWhenOneFails() {
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());
        when(dsParticipantRepository.bindMemberParticipant(eq(MEMBER), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("uniq_ds_participant_member_identifier"));

        assertThat(service.bindMembersIfAbsent(List.of(MEMBER, OTHER_MEMBER), true)).isEqualTo(1);

        verify(dsParticipantRepository).bindMemberParticipant(eq(OTHER_MEMBER), anyString(), anyString());
    }

    @Test
    void reportsAnUnknownRegisteredAddressInsteadOfPropagating() {
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());
        when(ownSecurityServerResolver.registeredAddress()).thenReturn(Optional.empty());

        assertThat(service.bindMembersIfAbsent(List.of(MEMBER), true)).isZero();

        verify(dsParticipantRepository, never()).bindMemberParticipant(any(), anyString(), anyString());
    }
}

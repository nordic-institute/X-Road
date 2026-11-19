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
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.ds.identity.RegisteredHostMatcher;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningTrigger;
import org.niis.xroad.securityserver.restapi.scheduling.NoopDataspaceParticipantProvisioningTrigger;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.memberDid;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.memberEntity;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.systemDid;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.systemEntity;

class DataspaceReIdentificationServiceTest {

    private static final ClientId OWNER = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "333");
    private static final SecurityServerId SERVER = SecurityServerId.Conf.create(OWNER, "SS0");
    private static final String NEW_ADDRESS = "ss0-new.example.org";
    private static final String OLD_AUTHORITY = "ss0-old.example.org:7183";
    private static final String NEW_AUTHORITY = NEW_ADDRESS + ":7183";

    private final OwnSecurityServerResolver ownSecurityServerResolver = mock(OwnSecurityServerResolver.class);
    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
    private final DataspaceReadinessPredicates readinessPredicates =
            spy(new DataspaceReadinessPredicates(globalConfProvider, null, null));
    private final DsParticipantRepository dsParticipantRepository = mock(DsParticipantRepository.class);
    private final DataspaceParticipantProvisioningTrigger provisioningTrigger =
            mock(NoopDataspaceParticipantProvisioningTrigger.class);
    private final DataspaceReIdentificationService service = new DataspaceReIdentificationService(
            ownSecurityServerResolver, readinessPredicates, dsParticipantRepository, provisioningTrigger);

    private DsParticipantEntity system;
    private DsParticipantEntity owner;
    private DsParticipantEntity member;

    @BeforeEach
    void setUp() {
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.Registered(SERVER, NEW_ADDRESS));
        when(globalConfProvider.getInstanceIdentifier()).thenReturn("DEV");
        when(globalConfProvider.getCredentialIssuerDids("DEV")).thenReturn(Set.of("did:web:issuer.example.org"));
        when(globalConfProvider.isValid()).thenReturn(true);
        doReturn(true).when(readinessPredicates).hasRegisteredAuthCert();
        when(dsParticipantRepository.retireDifferingBindings(any())).thenAnswer(invocation -> {
            RegisteredHostMatcher matcher = invocation.getArgument(0);
            var differing = List.of(system, owner, member).stream()
                    .filter(row -> row.getState() == ParticipantState.ACTIVE)
                    .filter(row -> !matcher.didHostMatches(row.getDid()))
                    .toList();
            differing.forEach(row -> row.setState(ParticipantState.DECOMMISSIONED));
            return differing.size();
        });
    }

    @Test
    void retiresTheSystemAndMemberBindingsAtTheOldHostAndStartsTheWorkerAfterwards() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);

        int retired = service.reIdentifyOnOperatorRequest();

        assertThat(retired).isEqualTo(3);
        assertThat(List.of(system, owner, member)).allMatch(row -> row.getState() == ParticipantState.DECOMMISSIONED);
        var order = inOrder(dsParticipantRepository, provisioningTrigger);
        order.verify(dsParticipantRepository).retireDifferingBindings(any());
        order.verify(provisioningTrigger).provisionParticipantAsync();
    }

    @Test
    void isRefusedAsNotNeededAndStartsNoWorkerWhenNoBindingDiffersFromTheRegisteredAddress() {
        bindAll(NEW_AUTHORITY, PublicationState.PUBLISHED);

        assertRefusedWith(ErrorMessage.RE_IDENTIFICATION_NOT_NEEDED);

        verify(dsParticipantRepository).retireDifferingBindings(any());
        verifyNoInteractions(provisioningTrigger);
        assertThat(List.of(system, owner, member)).allMatch(row -> row.getState() == ParticipantState.ACTIVE);
    }

    @Test
    void aSecondCallAfterTheFlipIsRefusedAsNotNeededAndStartsNoSecondWorker() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        service.reIdentifyOnOperatorRequest();

        assertRefusedWith(ErrorMessage.RE_IDENTIFICATION_NOT_NEEDED);

        verify(provisioningTrigger).provisionParticipantAsync();
        assertThat(List.of(system, owner, member)).allMatch(row -> row.getState() == ParticipantState.DECOMMISSIONED);
    }

    @Test
    void isRefusedWithTheAddressUnknownCodeWhenTheServerHasNoRegisteredAddress() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.NotRegistered(SERVER));

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_ADDRESS_UNKNOWN);
    }

    @Test
    void isRefusedWithTheAddressUnknownCodeWhenGlobalConfIsUnavailable() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(ownSecurityServerResolver.address())
                .thenReturn(new OwnAddress.GlobalConfUnavailable(new IllegalStateException("no globalconf")));

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_ADDRESS_UNKNOWN);
    }

    @Test
    void isRefusedWithTheAddressUnknownCodeWhenTheRegisteredAddressIsNotAUsableHost() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.Registered(SERVER, "not a host name"));

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_ADDRESS_UNKNOWN);
    }

    @Test
    void isRefusedWithTheCannotBindCodeWhenTheDataspaceIsNotReady() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(globalConfProvider.getCredentialIssuerDids("DEV")).thenReturn(Set.of());

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_CANNOT_BIND);
    }

    @Test
    void isRefusedWithTheCannotBindCodeWhenTheAuthCertIsNotRegistered() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        doReturn(false).when(readinessPredicates).hasRegisteredAuthCert();

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_CANNOT_BIND);
    }

    @Test
    void isRefusedWithTheCannotBindCodeWhenGlobalConfIsNotUsable() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(globalConfProvider.isValid()).thenReturn(false);

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_CANNOT_BIND);
    }

    private void assertRefusedWithNothingRetired(ErrorMessage expected) {
        assertRefusedWith(expected);

        verify(dsParticipantRepository, never()).retireDifferingBindings(any());
        verifyNoInteractions(provisioningTrigger);
        assertThat(List.of(system, owner, member)).allMatch(row -> row.getState() == ParticipantState.ACTIVE);
    }

    private void assertRefusedWith(ErrorMessage expected) {
        assertThatThrownBy(service::reIdentifyOnOperatorRequest)
                .isInstanceOf(ConflictException.class)
                .satisfies(thrown -> assertThat(((ConflictException) thrown).getErrorDeviation().code())
                        .isEqualTo(expected.build().code()));
    }

    private void bindAll(String authority, PublicationState publicationState) {
        system = systemEntity(systemDid(authority), publicationState);
        owner = memberEntity(OWNER, memberDid(OWNER, authority), publicationState);
        member = memberEntity(MEMBER, memberDid(MEMBER, authority), publicationState);
    }
}

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.ds.identity.RegisteredHostMatcher;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningWorker;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.memberDid;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.memberEntity;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.systemDid;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.systemEntity;

class DataspaceReIdentificationServiceTest {

    private static final ClientId OWNER = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "333");
    private static final SecurityServerId SERVER = SecurityServerId.Conf.create(OWNER, "SS0");
    private static final long SYSTEM_ID = 1L;
    private static final long OWNER_ID = 2L;
    private static final long MEMBER_ID = 3L;
    private static final String NEW_ADDRESS = "ss0-new.example.org";
    private static final String OLD_AUTHORITY = "ss0-old.example.org:7183";
    private static final String NEW_AUTHORITY = NEW_ADDRESS + ":7183";

    private final OwnSecurityServerResolver ownSecurityServerResolver = mock(OwnSecurityServerResolver.class);
    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
    private final DataspaceReadinessPredicates readinessPredicates =
            spy(new DataspaceReadinessPredicates(globalConfProvider, null, null));
    private final DataspaceDidAuthority didAuthority = mock(DataspaceDidAuthority.class);
    private final DsParticipantRepository dsParticipantRepository = mock(DsParticipantRepository.class);
    private final DataspaceParticipantProvisioningWorker provisioningTrigger =
            mock(DataspaceParticipantProvisioningWorker.class);
    private final DataspaceReIdentificationService service = new DataspaceReIdentificationService(
            ownSecurityServerResolver, readinessPredicates, didAuthority, dsParticipantRepository,
            provisioningTrigger);

    private DsParticipantEntity system;
    private DsParticipantEntity owner;
    private DsParticipantEntity member;

    @BeforeEach
    void setUp() {
        when(provisioningTrigger.isActive()).thenReturn(true);
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.Registered(SERVER, NEW_ADDRESS));
        when(globalConfProvider.getInstanceIdentifier()).thenReturn("DEV");
        when(globalConfProvider.getCredentialIssuerDids("DEV")).thenReturn(Set.of("did:web:issuer.example.org"));
        when(globalConfProvider.isValid()).thenReturn(true);
        doReturn(true).when(readinessPredicates).hasRegisteredAuthCert();
        when(didAuthority.mintSystemDid())
                .thenReturn(new DidMintResult.Minted(ParticipantIdentifierScheme.systemDid(NEW_AUTHORITY)));
        when(didAuthority.mintMemberDid(any())).thenAnswer(invocation -> new DidMintResult.Minted(
                ParticipantIdentifierScheme.memberDid(invocation.getArgument(0), NEW_AUTHORITY)));
        when(dsParticipantRepository.findDifferingBindings(any())).thenAnswer(invocation ->
                differingRows(invocation.getArgument(0)));
        when(dsParticipantRepository.retireBindings(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            var retiring = List.of(system, owner, member).stream()
                    .filter(row -> row.getState() == ParticipantState.ACTIVE)
                    .filter(row -> ids.contains(row.getId()))
                    .toList();
            retiring.forEach(row -> row.setState(ParticipantState.DECOMMISSIONED));
            return retiring.size();
        });
    }

    private List<DsParticipantEntity> differingRows(RegisteredHostMatcher matcher) {
        return List.of(system, owner, member).stream()
                .filter(row -> row.getState() == ParticipantState.ACTIVE)
                .filter(row -> !matcher.didHostMatches(row.getDid()))
                .toList();
    }

    @Test
    void retiresTheSystemAndMemberBindingsAtTheOldHostAndStartsTheWorkerAfterwards() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);

        int retired = service.reIdentifyOnOperatorRequest();

        assertThat(retired).isEqualTo(3);
        assertThat(List.of(system, owner, member)).allMatch(row -> row.getState() == ParticipantState.DECOMMISSIONED);
        var order = inOrder(dsParticipantRepository, provisioningTrigger);
        order.verify(dsParticipantRepository).retireBindings(List.of(SYSTEM_ID, OWNER_ID, MEMBER_ID));
        order.verify(provisioningTrigger).provisionParticipantAsync();
    }

    @Test
    void isRefusedAsNotNeededAndStartsNoWorkerWhenNoBindingDiffersFromTheRegisteredAddress() {
        bindAll(NEW_AUTHORITY, PublicationState.PUBLISHED);

        assertRefusedWith(ErrorMessage.RE_IDENTIFICATION_NOT_NEEDED);

        verify(dsParticipantRepository).retireBindings(List.of());
        verify(provisioningTrigger, never()).provisionParticipantAsync();
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

    @Test
    void isRefusedWithTheProvisioningInactiveCodeWhenOnlyTheNoOpTriggerIsWired() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        var inactiveTrigger = new NoopDataspaceParticipantProvisioningTrigger();
        var inactiveService = new DataspaceReIdentificationService(
                ownSecurityServerResolver, readinessPredicates, didAuthority, dsParticipantRepository, inactiveTrigger);

        assertThatThrownBy(inactiveService::reIdentifyOnOperatorRequest)
                .isInstanceOf(ConflictException.class)
                .satisfies(thrown -> assertThat(((ConflictException) thrown).getErrorDeviation().code())
                        .isEqualTo(ErrorMessage.RE_IDENTIFICATION_PROVISIONING_INACTIVE.build().code()));

        verify(dsParticipantRepository, never()).retireBindings(anyList());
        assertThat(List.of(system, owner, member)).allMatch(row -> row.getState() == ParticipantState.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(value = DidMintResult.Reason.class, names = {"INVALID_HOST", "DID_TOO_LONG"})
    void isRefusedWithTheCannotMintCodeWhenTheSystemDidCannotBeMintedForAnyReason(DidMintResult.Reason reason) {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(didAuthority.mintSystemDid()).thenReturn(new DidMintResult.Rejected(reason, "rejected"));

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_CANNOT_MINT);
    }

    @ParameterizedTest
    @EnumSource(value = DidMintResult.Reason.class, names = {"INVALID_HOST", "DID_TOO_LONG"})
    void isRefusedWithTheCannotMintCodeWhenAMemberDidCannotBeMintedForAnyReason(DidMintResult.Reason reason) {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(didAuthority.mintMemberDid(MEMBER)).thenReturn(new DidMintResult.Rejected(reason, "rejected"));

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_CANNOT_MINT);
    }

    @Test
    void checksOnlyTheDidsOfTheBindingsItWouldRetire() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        system = systemEntity(systemDid(NEW_AUTHORITY), PublicationState.PUBLISHED);
        when(didAuthority.mintSystemDid())
                .thenReturn(new DidMintResult.Rejected(DidMintResult.Reason.DID_TOO_LONG, "rejected"));

        int retired = service.reIdentifyOnOperatorRequest();

        assertThat(retired).isEqualTo(2);
        verify(didAuthority, never()).mintSystemDid();
        verify(didAuthority).mintMemberDid(OWNER);
        verify(didAuthority).mintMemberDid(MEMBER);
        verify(dsParticipantRepository).retireBindings(List.of(OWNER_ID, MEMBER_ID));
    }

    @Test
    void isRefusedWithTheCannotMintCodeWhenTheSystemDidCannotBeEncoded() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(didAuthority.mintSystemDid())
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.VALIDATION_ERROR, "cannot be encoded"));

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_CANNOT_MINT);
    }

    @Test
    void isRefusedWithTheCannotMintCodeWhenAMemberIdentifierCannotBeEncoded() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(didAuthority.mintMemberDid(MEMBER))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.VALIDATION_ERROR, "cannot be encoded"));

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_CANNOT_MINT);
    }

    @Test
    void isRefusedWithTheAddressUnknownCodeWhenTheRegisteredAddressIsGoneWhileTheSystemDidIsMinted() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(didAuthority.mintSystemDid())
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.DSP_PROVISIONING_FAILED, "address is gone"));

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_ADDRESS_UNKNOWN);
    }

    @Test
    void isRefusedWithTheAddressUnknownCodeWhenTheRegisteredAddressIsGoneWhileAMemberDidIsMinted() {
        bindAll(OLD_AUTHORITY, PublicationState.PUBLISHED);
        when(didAuthority.mintMemberDid(MEMBER))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.DSP_PROVISIONING_FAILED, "address is gone"));

        assertRefusedWithNothingRetired(ErrorMessage.RE_IDENTIFICATION_ADDRESS_UNKNOWN);
    }

    private void assertRefusedWithNothingRetired(ErrorMessage expected) {
        assertRefusedWith(expected);

        verify(dsParticipantRepository, never()).retireBindings(anyList());
        verify(provisioningTrigger, never()).provisionParticipantAsync();
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
        system.setId(SYSTEM_ID);
        owner = memberEntity(OWNER, memberDid(OWNER, authority), publicationState);
        owner.setId(OWNER_ID);
        member = memberEntity(MEMBER, memberDid(MEMBER, authority), publicationState);
        member.setId(MEMBER_ID);
    }
}

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
package org.niis.xroad.edc.extension.catalog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.ManagementServiceCodes;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.AccessRight;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceContextResolverTest {

    private static final String SYSTEM_CTX = ParticipantIdentifierScheme.SYSTEM_SEGMENT;

    private static final ClientId.Conf MEMBER = ClientId.Conf.create("DEV", "GOV", "1111");
    private static final ClientId.Conf MGMT_CLIENT = ClientId.Conf.create("DEV", "COM", "3333", "MANAGEMENT");

    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create("DEV", "GOV", "1111", "ss0");

    private static final ServiceId.Conf SUBSYSTEM_SERVICE =
            ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "getRecords");
    private static final ServiceId.Conf MGMT_SERVICE =
            ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "clientReg");

    private static final String MEMBER_CTX = ParticipantIdentifierScheme.memberCtxId(MEMBER);

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private ParticipantContextService participantContextService;

    private ServiceContextResolver resolver() {
        return new ServiceContextResolver(
                globalConfProvider, serverConfProvider, participantContextService);
    }

    @Test
    void resolveContextsReturnsEmptyListWhenOwningMemberHasNoProvisionedContext() {
        var result = resolver().resolveContexts(SUBSYSTEM_SERVICE, Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void resolveContextsReturnsOnlyMemberContextWhenProvisioned() {
        var result = resolver().resolveContexts(SUBSYSTEM_SERVICE, Set.of(MEMBER_CTX));

        assertThat(result).containsExactly(MEMBER_CTX);
    }

    @Test
    void subsystemScopedServiceCollapsesToOwningMemberContextNeverASubsystemDerivedOne() {
        var subsystemDerivedCtx = MEMBER_CTX + ":not-a-real-member-ctx";

        var result = resolver().resolveContexts(SUBSYSTEM_SERVICE, Set.of(MEMBER_CTX, subsystemDerivedCtx));

        assertThat(result).containsExactly(MEMBER_CTX);
    }

    @Test
    void managementRequestServiceReturnsEmptyListWhenOwningMemberHasNoProvisionedContext() {
        var result = resolver().resolveContexts(MGMT_SERVICE, Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void managementRequestServiceResolvesOwningMemberContextOnceProvisioned() {
        var mgmtMemberCtx = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "COM", "3333"));

        var result = resolver().resolveContexts(MGMT_SERVICE, Set.of(mgmtMemberCtx));

        assertThat(result).containsExactly(mgmtMemberCtx);
    }

    @Test
    void selectReturnsRequestedContextWhenItIsAmongResolvedContexts() {
        var selected = ServiceContextResolver.select(List.of(SYSTEM_CTX, MEMBER_CTX), MEMBER_CTX);

        assertThat(selected).isEqualTo(MEMBER_CTX);
    }

    @Test
    void selectFallsBackToFirstResolvedContextWhenNoneRequested() {
        var selected = ServiceContextResolver.select(List.of(SYSTEM_CTX, MEMBER_CTX), null);

        assertThat(selected).isEqualTo(SYSTEM_CTX);
    }

    @Test
    void selectReturnsNullWhenRequestedContextIsNotAmongResolvedContexts() {
        var selected = ServiceContextResolver.select(List.of(MEMBER_CTX), "some-other-context");

        assertThat(selected).isNull();
    }

    @Test
    void selectReturnsNullWhenRequestedContextIsAnotherMembersContextNotInTheResolvedList() {
        var otherMember = ClientId.Conf.create("DEV", "GOV", "2222");
        var otherMemberCtx = ParticipantIdentifierScheme.memberCtxId(otherMember);
        var thirdMemberCtx = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "GOV", "3333"));

        var selected = ServiceContextResolver.select(List.of(MEMBER_CTX, otherMemberCtx), thirdMemberCtx);

        assertThat(selected).isNull();
    }

    @Test
    void selectReturnsNullWhenResolvedContextsIsEmpty() {
        assertThat(ServiceContextResolver.select(List.of(), "some-other-context")).isNull();
        assertThat(ServiceContextResolver.select(List.of(), null)).isNull();
    }

    @Test
    void provisionedMemberContextIdsRecognisesThreeSegmentShapeAndExcludesNonMemberShapes() {
        when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of(
                participantContext(MEMBER_CTX), participantContext(SYSTEM_CTX))));

        var result = resolver().provisionedMemberContextIds();

        assertThat(result).containsExactly(MEMBER_CTX);
    }

    @Test
    void provisionedMemberContextIdsPropagatesWhenSearchFails() {
        when(participantContextService.search(any())).thenReturn(ServiceResult.unexpected("boom"));

        assertThatThrownBy(() -> resolver().provisionedMemberContextIds())
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void provisionedMemberContextIdsPropagatesWhenServiceThrows() {
        when(participantContextService.search(any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> resolver().provisionedMemberContextIds())
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void resolveContextsByIdReturnsEmptyListWhenOwningMemberHasNoProvisionedContext() {
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.notFound("no such context"));

        var result = resolver().resolveContextsById(SUBSYSTEM_SERVICE);

        assertThat(result).isEmpty();
    }

    @Test
    void resolveContextsByIdReturnsOnlyMemberContextWhenProvisioned() {
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.success(participantContext(MEMBER_CTX)));

        var result = resolver().resolveContextsById(SUBSYSTEM_SERVICE);

        assertThat(result).containsExactly(MEMBER_CTX);
    }

    @Test
    void resolveContextsByIdManagementRequestServiceReturnsEmptyListWhenOwningMemberHasNoProvisionedContext() {
        var mgmtMemberCtx = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "COM", "3333"));
        when(participantContextService.getParticipantContext(mgmtMemberCtx))
                .thenReturn(ServiceResult.notFound("no such context"));

        var result = resolver().resolveContextsById(MGMT_SERVICE);

        assertThat(result).isEmpty();
    }

    @Test
    void resolveContextsByIdDoesNotPerformFullEnumeration() {
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.notFound("no such context"));

        resolver().resolveContextsById(SUBSYSTEM_SERVICE);

        verify(participantContextService, never()).search(any());
    }

    @Test
    void resolveContextsByIdPropagatesOnUnexpectedFailure() {
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.unexpected("boom"));

        assertThatThrownBy(() -> resolver().resolveContextsById(SUBSYSTEM_SERVICE))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void resolveContextsByIdPropagatesWhenServiceThrows() {
        when(participantContextService.getParticipantContext(eq(MEMBER_CTX)))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> resolver().resolveContextsById(SUBSYSTEM_SERVICE))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void normalizeRequestedContextPassesThroughValidMemberCtx() {
        assertThat(resolver().normalizeRequestedContext(MEMBER_CTX)).isEqualTo(MEMBER_CTX);
    }

    @Test
    void normalizeRequestedContextPassesThroughSystemCtx() {
        assertThat(resolver().normalizeRequestedContext(SYSTEM_CTX)).isEqualTo(SYSTEM_CTX);
    }

    @Test
    void normalizeRequestedContextCollapsesNullAndGarbageToNull() {
        assertThat(resolver().normalizeRequestedContext(null)).isNull();
        assertThat(resolver().normalizeRequestedContext("not-a-real-ctx")).isNull();
        assertThat(resolver().normalizeRequestedContext(MEMBER_CTX + ":not-a-real-member-ctx")).isNull();
    }

    @Test
    void normalizeRequestedContextCollapsesSingleSegmentNonSystemStringToNull() {
        assertThat(resolver().normalizeRequestedContext("xroad-provider")).isNull();
    }

    // --- isSystemEligible / resolveSyntheticServices ---

    private static final ServiceId.Conf MGMT_ELIGIBLE_SERVICE =
            ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "clientReg");

    @Test
    void isSystemEligibleAcceptsVersionlessEligibleCode() {
        stubLiveManagementSubsystem();

        assertThat(resolver().isSystemEligible(MGMT_ELIGIBLE_SERVICE)).isTrue();
    }

    @Test
    void isSystemEligibleDoesNotConsultRealServiceConfiguration() {
        stubLiveManagementSubsystem();

        assertThat(resolver().isSystemEligible(MGMT_ELIGIBLE_SERVICE)).isTrue();

        verify(serverConfProvider, never()).getAllServices(any());
    }

    @Test
    void isSystemEligibleRejectsVersionedServiceIdEvenWhenCodeAndOwnerMatch() {
        // Version check is the cheap short-circuit, so no management-subsystem resolution is stubbed.
        var versioned = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "clientReg", "v1");

        assertThat(resolver().isSystemEligible(versioned)).isFalse();
    }

    @Test
    void isSystemEligibleRejectsAuthCertRegEvenWhenOwnerMatches() {
        // Service-code check is the cheap short-circuit, so no management-subsystem resolution is stubbed.
        var authCertReg = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "authCertReg");

        assertThat(resolver().isSystemEligible(authCertReg)).isFalse();
    }

    @Test
    void isSystemEligibleSkipsManagementSubsystemResolutionForIneligibleCode() {
        var authCertReg = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "authCertReg");

        assertThat(resolver().isSystemEligible(authCertReg)).isFalse();

        verify(globalConfProvider, never()).getManagementRequestService();
    }

    @Test
    void isSystemEligibleDegradesToFalseWhenManagementSubsystemResolutionThrows() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenThrow(new IllegalStateException("boom"));

        assertThat(resolver().isSystemEligible(MGMT_ELIGIBLE_SERVICE)).isFalse();
    }

    @Test
    void isSystemUnrestrictedByIdTrueForExistingEnabledServiceWithNoAccessRights() {
        when(serverConfProvider.serviceExists(MGMT_ELIGIBLE_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_ELIGIBLE_SERVICE)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(MGMT_ELIGIBLE_SERVICE)).thenReturn(List.of());

        assertThat(resolver().isSystemUnrestrictedById(MGMT_ELIGIBLE_SERVICE)).isTrue();
    }

    @Test
    void isSystemUnrestrictedByIdFalseWhenServiceDoesNotExist() {
        when(serverConfProvider.serviceExists(MGMT_ELIGIBLE_SERVICE)).thenReturn(false);

        assertThat(resolver().isSystemUnrestrictedById(MGMT_ELIGIBLE_SERVICE)).isFalse();
    }

    @Test
    void isSystemUnrestrictedByIdFalseWhenServiceDisabled() {
        when(serverConfProvider.serviceExists(MGMT_ELIGIBLE_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_ELIGIBLE_SERVICE)).thenReturn("Maintenance");

        assertThat(resolver().isSystemUnrestrictedById(MGMT_ELIGIBLE_SERVICE)).isFalse();
    }

    @Test
    void isSystemUnrestrictedByIdFalseWhenAccessRightsConfigured() {
        when(serverConfProvider.serviceExists(MGMT_ELIGIBLE_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_ELIGIBLE_SERVICE)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(MGMT_ELIGIBLE_SERVICE)).thenReturn(List.of(new AccessRight()));

        assertThat(resolver().isSystemUnrestrictedById(MGMT_ELIGIBLE_SERVICE)).isFalse();
    }

    @Test
    void shouldPublishUnrestrictedSystemEntryTrueWhenEligibleAndNoAccessRights() {
        assertThat(resolver().shouldPublishUnrestrictedSystemEntry(true, List.of())).isTrue();
    }

    @Test
    void shouldPublishUnrestrictedSystemEntryFalseWhenNotEligible() {
        assertThat(resolver().shouldPublishUnrestrictedSystemEntry(false, List.of())).isFalse();
    }

    @Test
    void shouldPublishUnrestrictedSystemEntryFalseWhenAccessRightsConfigured() {
        assertThat(resolver().shouldPublishUnrestrictedSystemEntry(true, List.of(new AccessRight()))).isFalse();
    }

    @Test
    void resolveSyntheticServicesResolvesManagementSubsystemOnce() {
        stubEligibleManagementSubsystem();

        var result = resolver().resolveSyntheticServices();

        assertThat(result).hasSize(ManagementServiceCodes.DSP_NEGOTIATED.size());
        verify(serverConfProvider, times(1)).getAllServices(MGMT_CLIENT);
    }

    @Test
    void resolveSyntheticServicesReturnsEmptyListWhenNoManagementSubsystem() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);

        var result = resolver().resolveSyntheticServices();

        assertThat(result).isEmpty();
    }

    @Test
    void resolveSyntheticServicesReturnsEmptyListWhenManagementSubsystemHasRealServices() {
        stubLiveManagementSubsystem();
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_ELIGIBLE_SERVICE));

        var result = resolver().resolveSyntheticServices();

        assertThat(result).isEmpty();
    }

    // --- isSystemPublished / isSystemSyntheticEligible ---

    private static final ServiceId.Conf OTHER_MGMT_ELIGIBLE_SERVICE =
            ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "maintenanceModeEnable");

    @Test
    void isSystemPublishedTrueForRealEnabledService() {
        when(serverConfProvider.serviceExists(MGMT_ELIGIBLE_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_ELIGIBLE_SERVICE)).thenReturn(null);

        assertThat(resolver().isSystemPublished(MGMT_ELIGIBLE_SERVICE)).isTrue();
    }

    @Test
    void isSystemPublishedFalseForRealDisabledService() {
        when(serverConfProvider.serviceExists(MGMT_ELIGIBLE_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_ELIGIBLE_SERVICE)).thenReturn("Maintenance");
        when(serverConfProvider.getServiceAccessRights(MGMT_ELIGIBLE_SERVICE)).thenReturn(List.of());

        assertThat(resolver().isSystemPublished(MGMT_ELIGIBLE_SERVICE)).isFalse();
    }

    @Test
    void isSystemPublishedTrueForRealDisabledServiceWithConfiguredAccessRights() {
        when(serverConfProvider.serviceExists(MGMT_ELIGIBLE_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_ELIGIBLE_SERVICE)).thenReturn("Maintenance");
        when(serverConfProvider.getServiceAccessRights(MGMT_ELIGIBLE_SERVICE)).thenReturn(List.of(new AccessRight()));

        assertThat(resolver().isSystemPublished(MGMT_ELIGIBLE_SERVICE)).isTrue();
    }

    @Test
    void isSystemPublishedTrueForSyntheticFallbackCoveredService() {
        when(serverConfProvider.serviceExists(MGMT_ELIGIBLE_SERVICE)).thenReturn(false);
        stubEligibleManagementSubsystem();

        assertThat(resolver().isSystemPublished(MGMT_ELIGIBLE_SERVICE)).isTrue();
    }

    @Test
    void isSystemPublishedFalseWhenNeitherRealNorSyntheticallyCovered() {
        // Partial migration: MGMT_ELIGIBLE_SERVICE is real, so the whole subsystem no longer
        // qualifies for the synthetic fallback, but OTHER_MGMT_ELIGIBLE_SERVICE was never configured.
        when(serverConfProvider.serviceExists(OTHER_MGMT_ELIGIBLE_SERVICE)).thenReturn(false);
        stubLiveManagementSubsystem();
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_ELIGIBLE_SERVICE));

        assertThat(resolver().isSystemPublished(OTHER_MGMT_ELIGIBLE_SERVICE)).isFalse();
    }

    @Test
    void isSystemSyntheticEligibleTrueWhenSubsystemHasNoRealServices() {
        stubEligibleManagementSubsystem();

        assertThat(resolver().isSystemSyntheticEligible(MGMT_ELIGIBLE_SERVICE)).isTrue();
    }

    @Test
    void isSystemSyntheticEligibleFalseWhenSubsystemHasRealServices() {
        stubLiveManagementSubsystem();
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_ELIGIBLE_SERVICE));

        assertThat(resolver().isSystemSyntheticEligible(OTHER_MGMT_ELIGIBLE_SERVICE)).isFalse();
    }

    @Test
    void isSystemSyntheticEligibleFalseWhenNoManagementSubsystem() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);

        assertThat(resolver().isSystemSyntheticEligible(MGMT_ELIGIBLE_SERVICE)).isFalse();
    }

    private void stubLiveManagementSubsystem() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);
    }

    private void stubEligibleManagementSubsystem() {
        stubLiveManagementSubsystem();
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of());
    }

    private static ParticipantContext participantContext(String contextId) {
        return ParticipantContext.Builder.newInstance()
                .participantContextId(contextId)
                .identity("did:web:example.com:v1:" + contextId)
                .build();
    }
}

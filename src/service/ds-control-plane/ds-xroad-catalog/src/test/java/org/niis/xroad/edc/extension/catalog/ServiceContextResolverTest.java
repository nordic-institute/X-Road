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
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

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

    private static final String HOST_CTX = "xroad-provider";
    private static final String MGMT_CTX = "xroad-provider-mgmt";
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
                HOST_CTX, MGMT_CTX, SYSTEM_CTX, globalConfProvider, serverConfProvider, participantContextService);
    }

    @Test
    void resolveEnabledReturnsOnlyLegacyHostContextWhenOwningMemberHasNoProvisionedContext() {
        var result = resolver().resolveEnabled(SUBSYSTEM_SERVICE, Set.of());

        assertThat(result).containsExactly(HOST_CTX);
    }

    @Test
    void resolveEnabledIncludesOwningMemberContextWhenProvisioned() {
        var result = resolver().resolveEnabled(SUBSYSTEM_SERVICE, Set.of(MEMBER_CTX));

        assertThat(result).containsExactly(HOST_CTX, MEMBER_CTX);
    }

    @Test
    void subsystemScopedServiceCollapsesToOwningMemberContextNeverASubsystemDerivedOne() {
        var subsystemDerivedCtx = MEMBER_CTX + ":not-a-real-member-ctx";

        var result = resolver().resolveEnabled(SUBSYSTEM_SERVICE, Set.of(MEMBER_CTX, subsystemDerivedCtx));

        assertThat(result).containsExactly(HOST_CTX, MEMBER_CTX);
    }

    @Test
    void managementRequestServiceLegacyPublicationContextIsManagementNotHost() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        var result = resolver().resolveEnabled(MGMT_SERVICE, Set.of());

        assertThat(result).containsExactly(MGMT_CTX);
    }

    @Test
    void selectReturnsRequestedContextWhenItIsAmongResolvedContexts() {
        var selected = ServiceContextResolver.select(List.of(HOST_CTX, MEMBER_CTX), MEMBER_CTX);

        assertThat(selected).isEqualTo(MEMBER_CTX);
    }

    @Test
    void selectFallsBackToLegacyHostContextWhenNoneRequested() {
        var selected = ServiceContextResolver.select(List.of(HOST_CTX, MEMBER_CTX), null);

        assertThat(selected).isEqualTo(HOST_CTX);
    }

    @Test
    void selectFallsBackToLegacyHostContextWhenRequestedContextIsNotAmongResolvedContexts() {
        var selected = ServiceContextResolver.select(List.of(HOST_CTX), "some-other-context");

        assertThat(selected).isEqualTo(HOST_CTX);
    }

    @Test
    void provisionedMemberContextIdsRecognisesThreeSegmentShapeAndExcludesHostAndManagement() {
        when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of(
                participantContext(MEMBER_CTX), participantContext(HOST_CTX), participantContext(MGMT_CTX))));

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
    void resolveEnabledByIdReturnsOnlyLegacyHostContextWhenOwningMemberHasNoProvisionedContext() {
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.notFound("no such context"));

        var result = resolver().resolveEnabledById(SUBSYSTEM_SERVICE);

        assertThat(result).containsExactly(HOST_CTX);
    }

    @Test
    void resolveEnabledByIdIncludesOwningMemberContextWhenProvisioned() {
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.success(participantContext(MEMBER_CTX)));

        var result = resolver().resolveEnabledById(SUBSYSTEM_SERVICE);

        assertThat(result).containsExactly(HOST_CTX, MEMBER_CTX);
    }

    @Test
    void resolveEnabledByIdDoesNotPerformFullEnumeration() {
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.notFound("no such context"));

        resolver().resolveEnabledById(SUBSYSTEM_SERVICE);

        verify(participantContextService, never()).search(any());
    }

    @Test
    void resolveEnabledByIdPropagatesOnUnexpectedFailure() {
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.unexpected("boom"));

        assertThatThrownBy(() -> resolver().resolveEnabledById(SUBSYSTEM_SERVICE))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void resolveEnabledByIdPropagatesWhenServiceThrows() {
        when(participantContextService.getParticipantContext(eq(MEMBER_CTX)))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> resolver().resolveEnabledById(SUBSYSTEM_SERVICE))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void normalizeRequestedContextPassesThroughHostAndManagementAndValidMemberCtx() {
        assertThat(resolver().normalizeRequestedContext(HOST_CTX)).isEqualTo(HOST_CTX);
        assertThat(resolver().normalizeRequestedContext(MGMT_CTX)).isEqualTo(MGMT_CTX);
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

    // --- isSystemEligible / resolveSyntheticServices / selectBuiltinContextId ---

    private static final ServiceId.Conf MGMT_ELIGIBLE_SERVICE =
            ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "clientReg");

    @Test
    void isSystemEligibleAcceptsVersionlessEligibleCode() {
        stubEligibleManagementSubsystem();

        assertThat(resolver().isSystemEligible(MGMT_ELIGIBLE_SERVICE)).isTrue();
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
    void resolveSyntheticServicesResolvesManagementSubsystemOnceForBothLists() {
        stubEligibleManagementSubsystem();

        var result = resolver().resolveSyntheticServices();

        assertThat(result.managementEntries()).hasSize(ManagementServiceCatalog.SERVICE_CODES.size());
        assertThat(result.systemEntries()).hasSize(ManagementServiceCatalog.SYSTEM_SERVICE_CODES.size());
        verify(serverConfProvider, times(1)).getAllServices(MGMT_CLIENT);
    }

    @Test
    void resolveSyntheticServicesReturnsEmptyListsWhenNoManagementSubsystem() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);

        var result = resolver().resolveSyntheticServices();

        assertThat(result.managementEntries()).isEmpty();
        assertThat(result.systemEntries()).isEmpty();
    }

    @Test
    void selectBuiltinContextIdReturnsSystemWhenSystemRequested() {
        assertThat(resolver().selectBuiltinContextId(SYSTEM_CTX)).isEqualTo(SYSTEM_CTX);
    }

    @Test
    void selectBuiltinContextIdReturnsManagementOtherwise() {
        assertThat(resolver().selectBuiltinContextId(HOST_CTX)).isEqualTo(MGMT_CTX);
        assertThat(resolver().selectBuiltinContextId(null)).isEqualTo(MGMT_CTX);
    }

    private void stubEligibleManagementSubsystem() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of());
    }

    private static ParticipantContext participantContext(String contextId) {
        return ParticipantContext.Builder.newInstance()
                .participantContextId(contextId)
                .identity("did:web:example.com:v1:" + contextId)
                .build();
    }
}

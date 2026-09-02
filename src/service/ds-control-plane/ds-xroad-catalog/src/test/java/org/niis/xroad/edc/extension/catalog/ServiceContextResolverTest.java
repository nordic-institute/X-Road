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
import ee.ria.xroad.common.identifier.ServiceId;

import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceContextResolverTest {

    private static final String HOST_CONTEXT_ID = "xroad-provider";
    private static final String MGMT_CONTEXT_ID = "xroad-provider-mgmt";

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "GOV", "1111");
    private static final ClientId OTHER_MEMBER = ClientId.Conf.create("DEV", "COM", "2222");
    private static final ClientId SUBSYSTEM_A = ClientId.Conf.create("DEV", "GOV", "1111", "SubsystemA");
    private static final ClientId SUBSYSTEM_B = ClientId.Conf.create("DEV", "GOV", "1111", "SubsystemB");
    private static final ClientId MGMT_CLIENT = ClientId.Conf.create("DEV", "COM", "3333", "MANAGEMENT");

    private static final ServiceId.Conf SERVICE = ServiceId.Conf.create(SUBSYSTEM_A, "getRecords");
    private static final ServiceId.Conf OTHER_SUBSYSTEM_SERVICE = ServiceId.Conf.create(SUBSYSTEM_B, "submitForm");
    private static final ServiceId.Conf MGMT_SERVICE = ServiceId.Conf.create(MGMT_CLIENT, "clientReg");

    @Mock
    private ParticipantContextService participantContextService;

    @Mock
    private GlobalConfProvider globalConfProvider;

    private ServiceContextResolver resolver() {
        return new ServiceContextResolver(participantContextService, globalConfProvider, HOST_CONTEXT_ID, MGMT_CONTEXT_ID);
    }

    private void stubPersistedContexts(String... ctxIds) {
        var contexts = List.of(ctxIds).stream()
                .map(id -> ParticipantContext.Builder.newInstance().participantContextId(id).identity(id).build())
                .toList();
        when(participantContextService.search(any())).thenReturn(ServiceResult.success(contexts));
    }

    @Test
    void resolveContextsReturnsHostContextOnlyWhenMemberNotProvisioned() {
        stubPersistedContexts();

        var result = resolver().resolveContexts(SERVICE);

        assertThat(result).containsExactly(HOST_CONTEXT_ID);
    }

    @Test
    void resolveContextsIncludesMemberContextWhenProvisioned() {
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        stubPersistedContexts(memberCtxId);

        var result = resolver().resolveContexts(SERVICE);

        assertThat(result).containsExactly(HOST_CONTEXT_ID, memberCtxId);
    }

    @Test
    void resolveContextsCollapsesSubsystemToMemberContext() {
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        stubPersistedContexts(memberCtxId);

        var subsystemAResult = resolver().resolveContexts(SERVICE);
        var subsystemBResult = resolver().resolveContexts(OTHER_SUBSYSTEM_SERVICE);

        assertThat(subsystemAResult).containsExactly(HOST_CONTEXT_ID, memberCtxId);
        assertThat(subsystemBResult).containsExactly(HOST_CONTEXT_ID, memberCtxId);
    }

    @Test
    void resolveContextsIgnoresContextProvisionedForADifferentMember() {
        stubPersistedContexts(ParticipantIdentifierScheme.memberCtxId(OTHER_MEMBER));

        var result = resolver().resolveContexts(SERVICE);

        assertThat(result).containsExactly(HOST_CONTEXT_ID);
    }

    @Test
    void resolveContextsClassifiesHostAndManagementEntriesAsNonMemberBySegmentShape() {
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        stubPersistedContexts(HOST_CONTEXT_ID, MGMT_CONTEXT_ID, memberCtxId);

        var result = resolver().resolveContexts(SERVICE);

        assertThat(result).containsExactly(HOST_CONTEXT_ID, memberCtxId);
    }

    @Test
    void resolveContextsSkipsAMalformedMemberShapedEntryInsteadOfFailing() {
        stubPersistedContexts("not:a:validsegment%zz");

        var result = resolver().resolveContexts(SERVICE);

        assertThat(result).containsExactly(HOST_CONTEXT_ID);
    }

    @Test
    void resolveContextsClassifiesAColonBearingLegacyHostContextAsNonMemberBySegmentShape() {
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        stubPersistedContexts("host:7183", memberCtxId);

        var result = resolver().resolveContexts(SERVICE);

        assertThat(result).containsExactly(HOST_CONTEXT_ID, memberCtxId);
    }

    @Test
    void resolveContextsDropsAPathologicalThreeSegmentHostnameViaTheMalformedDecodePath() {
        // Three colon segments (matches the member ctx-id shape) but the middle segment is blank,
        // so decoding fails building a ClientId rather than at percent-escape parsing.
        stubPersistedContexts("prod-eu::gateway01");

        var result = resolver().resolveContexts(SERVICE);

        assertThat(result).containsExactly(HOST_CONTEXT_ID);
    }

    @Test
    void resolveContextsReturnsHostContextOnlyWhenPersistedStoreSearchFails() {
        when(participantContextService.search(any())).thenReturn(ServiceResult.notFound("boom"));

        var result = resolver().resolveContexts(SERVICE);

        assertThat(result).containsExactly(HOST_CONTEXT_ID);
    }

    @Test
    void resolveContextsReturnsManagementContextOnlyForManagementService() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        var result = resolver().resolveContexts(MGMT_SERVICE);

        assertThat(result).containsExactly(MGMT_CONTEXT_ID);
    }

    @Test
    void resolveContextsForManagementServiceNeverConsultsThePersistedStore() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        resolver().resolveContexts(MGMT_SERVICE);

        verifyNoInteractions(participantContextService);
    }

    @Test
    void selectContextReturnsRequestedContextWhenItIsAmongResolvedContexts() {
        var contexts = List.of(HOST_CONTEXT_ID, "member-ctx");

        var result = ServiceContextResolver.selectContext(contexts, "member-ctx");

        assertThat(result).isEqualTo("member-ctx");
    }

    @Test
    void selectContextFallsBackToFirstContextWhenNoneRequested() {
        var contexts = List.of(HOST_CONTEXT_ID, "member-ctx");

        var result = ServiceContextResolver.selectContext(contexts, null);

        assertThat(result).isEqualTo(HOST_CONTEXT_ID);
    }

    @Test
    void selectContextFallsBackToFirstContextWhenRequestedContextIsNotLegitimateForThisService() {
        var contexts = List.of(HOST_CONTEXT_ID, "member-ctx");

        var result = ServiceContextResolver.selectContext(contexts, "some-unrelated-context");

        assertThat(result).isEqualTo(HOST_CONTEXT_ID);
    }
}

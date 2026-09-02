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

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.query.QuerySpec;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resolves the participant contexts a service should be published under, replacing the
 * per-store {@code resolveContextId} logic previously duplicated across the ServerConf-backed
 * catalog stores. The MANAGEMENT subsystem's own services keep their distinct management
 * context (avoiding a self-negotiation identity collision); every other service is published
 * under the legacy host context, plus — when the Security Server's persisted participant-context
 * store holds one for the service's owning member — that member's context. Subsystem ownership
 * always collapses to the owning member: the subsystem code plays no part in context resolution.
 *
 * <p>The persisted participant-context store (populated exclusively by the SS provisioning gRPC
 * push) is the sole source of truth for member contexts: a member whose context has not been
 * provisioned yet simply yields no member context, no error. Member ctx-ids are told apart from
 * the legacy host and management contexts by shape — a member ctx-id has exactly three
 * colon-separated segments — and decoded back to member identifiers with the XRDADR-41 v1
 * scheme.
 */
@Slf4j
@RequiredArgsConstructor
class ServiceContextResolver {

    private static final int MEMBER_CTX_ID_SEGMENT_COUNT = 3;
    private static final String CTX_ID_SEGMENT_SEPARATOR = ":";

    private final ParticipantContextService participantContextService;
    private final GlobalConfProvider globalConfProvider;
    private final String hostParticipantContextId;
    private final String managementParticipantContextId;

    /**
     * @return the participant contexts to publish {@code serviceId} under; the legacy host or
     *         management context always comes first, followed by the owning member's context
     *         when provisioned
     */
    List<String> resolveContexts(ServiceId serviceId) {
        if (isManagementService(serviceId)) {
            return List.of(managementParticipantContextId);
        }
        var contexts = new ArrayList<String>();
        contexts.add(hostParticipantContextId);
        findMemberContext(toMemberOwner(serviceId.getClientId())).ifPresent(contexts::add);
        return contexts;
    }

    /**
     * Picks which of {@code resolveContexts(serviceId)}'s contexts a by-id lookup should tag its
     * single returned row with. EDC validates a by-id row's ownership against the participant
     * context the caller's request was addressed to, but the by-id store SPIs carry no context
     * parameter — {@code requestedParticipantContextId} is that context, recovered out-of-band
     * (see {@link DspParticipantContextHolder}). When it is one of the contexts legitimately
     * published for this service, that context wins; otherwise (no in-flight DSP request, or a
     * context this service isn't published under) the pre-existing default — the first,
     * deterministic context — is used.
     *
     * @return the requested context when it is among {@code contexts}, else {@code contexts.getFirst()}
     */
    static String selectContext(List<String> contexts, @Nullable String requestedParticipantContextId) {
        if (requestedParticipantContextId != null && contexts.contains(requestedParticipantContextId)) {
            return requestedParticipantContextId;
        }
        return contexts.getFirst();
    }

    private boolean isManagementService(ServiceId serviceId) {
        var mgmtService = globalConfProvider.getManagementRequestService();
        return mgmtService != null && mgmtService.equals(serviceId.getClientId());
    }

    private Optional<String> findMemberContext(ClientId member) {
        return persistedMemberContextIds()
                .filter(ctxId -> member.equals(decodeMemberCtxId(ctxId)))
                .findFirst();
    }

    private Stream<String> persistedMemberContextIds() {
        return persistedContextIds().filter(ServiceContextResolver::hasMemberCtxIdShape);
    }

    private Stream<String> persistedContextIds() {
        var result = participantContextService.search(QuerySpec.max());
        if (result.failed()) {
            log.warn("Failed to enumerate persisted participant contexts: {}", result.getFailureDetail());
            return Stream.empty();
        }
        return result.getContent().stream().map(ParticipantContext::getParticipantContextId);
    }

    private static boolean hasMemberCtxIdShape(String ctxId) {
        return ctxId.split(CTX_ID_SEGMENT_SEPARATOR, -1).length == MEMBER_CTX_ID_SEGMENT_COUNT;
    }

    @Nullable
    private static ClientId decodeMemberCtxId(String ctxId) {
        try {
            return ParticipantIdentifierScheme.decodeMemberCtxId(ctxId);
        } catch (XrdRuntimeException e) {
            log.warn("Persisted participant context '{}' has member ctx-id shape but failed to decode: {}",
                    ctxId, e.getMessage());
            return null;
        }
    }

    private static ClientId toMemberOwner(ClientId owner) {
        return owner.getSubsystemCode() == null
                ? owner
                : ClientId.Conf.create(owner.getXRoadInstance(), owner.getMemberClass(), owner.getMemberCode());
    }
}

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
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Derives the dataspace participant contexts a service is published under, from the member that
 * owns it, and selects the record matching the context a DSP request was addressed to. Replaces
 * the byte-identical {@code resolveContextId} copies previously duplicated across the three
 * ServerConf-backed catalog stores.
 *
 * <p>A subsystem-scoped service collapses to its owning member before member-context resolution,
 * since subsystems never hold participant identity (XRDADR-41). Provisioned member contexts are
 * read from EDC's {@link ParticipantContextService} and recognised by their three-segment ctx-id
 * shape, which separates them from the host and management ctx-ids held in the same store.
 */
@Slf4j
@RequiredArgsConstructor
class ServiceContextResolver {

    private final String hostParticipantContextId;
    private final String managementParticipantContextId;
    private final String systemParticipantContextId;
    private final GlobalConfProvider globalConfProvider;
    private final ParticipantContextService participantContextService;

    /**
     * The contexts an enabled service is published under, besides its always-present
     * management-context copy: the legacy host context first — or the management context, for the
     * MANAGEMENT subsystem's own service — followed by the owning member's context if one is
     * provisioned. The first entry is always the legacy publication context.
     *
     * @param serviceId the service to resolve contexts for
     * @param provisionedMemberContextIds the currently provisioned member contexts, from {@link #provisionedMemberContextIds()}
     */
    List<String> resolveEnabled(ServiceId serviceId, Set<String> provisionedMemberContextIds) {
        var contexts = new ArrayList<String>(2);
        contexts.add(legacyPublicationContextId(serviceId));
        memberContextId(serviceId.getClientId(), provisionedMemberContextIds).ifPresent(contexts::add);
        return List.copyOf(contexts);
    }

    /**
     * Same contract as {@link #resolveEnabled(ServiceId, Set)}, for the by-id cache-miss path: tests
     * the owning member's ctx-id with a single direct {@link ParticipantContextService#getParticipantContext}
     * lookup instead of requiring the full {@link #provisionedMemberContextIds()} enumeration — a
     * by-id lookup only ever needs to know about the one ctx-id it can derive from the service.
     *
     * @param serviceId the service to resolve contexts for
     */
    List<String> resolveEnabledById(ServiceId serviceId) {
        var contexts = new ArrayList<String>(2);
        contexts.add(legacyPublicationContextId(serviceId));
        memberContextIdById(serviceId.getClientId()).ifPresent(contexts::add);
        return List.copyOf(contexts);
    }

    /**
     * Picks the record matching the request's addressed context, if it is one of
     * {@code resolvedContexts}; otherwise falls back to the legacy host context, which by
     * {@link #resolveEnabled(ServiceId, Set)}'s contract is always the first entry.
     */
    static String select(List<String> resolvedContexts, @Nullable String requestedParticipantContextId) {
        if (requestedParticipantContextId != null && resolvedContexts.contains(requestedParticipantContextId)) {
            return requestedParticipantContextId;
        }
        return resolvedContexts.getFirst();
    }

    /**
     * Normalizes a requested participant context for use as a by-id cache key: a value that is
     * neither the host context, the management context, the SYSTEM context, nor syntactically a
     * valid member ctx-id collapses to {@code null} — the same key as "no context requested" — so
     * that distinct garbage input never mints a distinct cache entry for what is, in every case,
     * the same legacy-fallback record. The cache itself stays unaware of ctx-id scheme rules; this
     * is the one place that decides what a plausible context looks like.
     */
    @Nullable
    String normalizeRequestedContext(@Nullable String requestedParticipantContextId) {
        if (requestedParticipantContextId == null) {
            return null;
        }
        if (requestedParticipantContextId.equals(hostParticipantContextId)
                || requestedParticipantContextId.equals(managementParticipantContextId)
                || requestedParticipantContextId.equals(systemParticipantContextId)
                || isMemberContextShape(requestedParticipantContextId)) {
            return requestedParticipantContextId;
        }
        return null;
    }

    /**
     * The currently provisioned member participant contexts, recognised by their three-segment
     * ctx-id shape.
     *
     * @throws XrdRuntimeException if the participant-context service cannot be reached — a caller
     *     enumerating the catalog must fail rather than cache an incomplete view of provisioned
     *     members for the full cache TTL
     */
    Set<String> provisionedMemberContextIds() {
        var result = search();
        if (result == null || result.failed()) {
            throw XrdRuntimeException.systemException(ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED,
                    "Failed to list provisioned participant contexts: %s",
                    result == null ? "no result" : result.getFailureDetail());
        }
        return result.getContent().stream()
                .map(ParticipantContext::getParticipantContextId)
                .filter(ServiceContextResolver::isMemberContextShape)
                .collect(Collectors.toUnmodifiableSet());
    }

    private ServiceResult<Collection<ParticipantContext>> search() {
        try {
            return participantContextService.search(QuerySpec.max());
        } catch (RuntimeException e) {
            throw XrdRuntimeException.systemException(ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED, e,
                    "Failed to list provisioned participant contexts: %s", e.getMessage());
        }
    }

    /** MANAGEMENT subsystem uses a distinct DSP identity to avoid self-negotiation constraint violations. */
    private String legacyPublicationContextId(ServiceId serviceId) {
        var mgmtService = globalConfProvider.getManagementRequestService();
        return (mgmtService != null && mgmtService.equals(serviceId.getClientId()))
                ? managementParticipantContextId
                : hostParticipantContextId;
    }

    private Optional<String> memberContextId(ClientId owner, Set<String> provisionedMemberContextIds) {
        var ctxId = ParticipantIdentifierScheme.memberCtxId(toMemberId(owner));
        return provisionedMemberContextIds.contains(ctxId) ? Optional.of(ctxId) : Optional.empty();
    }

    /**
     * Tests provisioning of the owner's derived ctx-id with one {@link ParticipantContextService#getParticipantContext}
     * call. {@link ServiceFailure.Reason#NOT_FOUND} means the context is not provisioned; any other
     * failure reason propagates, matching {@link #provisionedMemberContextIds()}'s fail-loud contract.
     */
    private Optional<String> memberContextIdById(ClientId owner) {
        var ctxId = ParticipantIdentifierScheme.memberCtxId(toMemberId(owner));
        ServiceResult<ParticipantContext> result;
        try {
            result = participantContextService.getParticipantContext(ctxId);
        } catch (RuntimeException e) {
            throw XrdRuntimeException.systemException(ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED, e,
                    "Failed to look up participant context '%s': %s", ctxId, e.getMessage());
        }
        if (result.succeeded()) {
            return Optional.of(ctxId);
        }
        if (result.reason() == ServiceFailure.Reason.NOT_FOUND) {
            return Optional.empty();
        }
        throw XrdRuntimeException.systemException(ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED,
                "Failed to look up participant context '%s': %s", ctxId, result.getFailureDetail());
    }

    /** Collapses a subsystem-scoped owner to its owning member; a subsystem never holds participant identity. */
    private static ClientId toMemberId(ClientId owner) {
        return ClientId.Conf.create(owner.getXRoadInstance(), owner.getMemberClass(), owner.getMemberCode());
    }

    private static boolean isMemberContextShape(@Nullable String contextId) {
        if (contextId == null) {
            return false;
        }
        try {
            ParticipantIdentifierScheme.decodeMemberCtxId(contextId);
            return true;
        } catch (XrdRuntimeException e) {
            return false;
        }
    }
}

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

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.AccessRight;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Read-only {@link PolicyDefinitionStore} backed by serverconf access rights.
 * Emits one ODRL {@link PolicyDefinition} per (service, subject) pair.
 */
@Slf4j
@RequiredArgsConstructor
class PolicyDefinitionServerConfStore implements PolicyDefinitionStore {

    private static final String READ_ONLY_MESSAGE = "Read-only: managed by ServerConf";

    private final ServerConfProvider serverConfProvider;
    private final PolicyMapper policyMapper;
    private final CatalogContextIds contextIds;
    private final BuiltinServiceCatalog builtinServiceCatalog;
    private final StoreEnumerationCache<PolicyDefinition> cache;
    private final ServiceContextResolver serviceContextResolver;
    private final RequestedParticipantContext requestedParticipantContext;
    private final QueryEvaluator<PolicyDefinition> queryEvaluator =
            new QueryEvaluator<>(PolicyDefinition::getId, PolicyDefinition::getParticipantContextId);

    @Override
    @Nullable
    @WithSpan("dsp-find-acl")
    public PolicyDefinition findById(@SpanAttribute String policyId) {
        var cacheKeyContext = serviceContextResolver.normalizeRequestedContext(requestedParticipantContext.get());
        return cache.findById(policyId, cacheKeyContext, () -> findByIdInternal(policyId));
    }

    @Nullable
    private PolicyDefinition findByIdInternal(String policyId) {
        log.trace("findById policyId={}", policyId);
        if (policyId == null || policyId.isBlank()) {
            log.trace("findById policyId blank, returning null");
            return null;
        }

        var builtinServiceId = builtinServiceCatalog.findServiceId(policyId);
        if (builtinServiceId != null) {
            log.trace("findById policyId={} matched builtin", policyId);
            return toBuiltinPolicyDefinition(policyId,
                    serviceContextResolver.selectBuiltinContextId(requestedParticipantContext.get()));
        }

        if (serviceContextResolver.isSystemAddressed(requestedParticipantContext.get())) {
            var systemResult = findSystemPolicyDefinition(policyId);
            if (systemResult != null) {
                return systemResult;
            }
            if (policyId.endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX)) {
                log.trace("findById policyId={} SYSTEM owner-only candidate did not resolve", policyId);
                return null;
            }
            // Fall through: a SYSTEM-eligible real service with configured access rights
            // publishes its per-subject compound id there instead of the plain/owner-only forms.
        } else if (policyId.endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX)) {
            return findOwnerOnlyPolicyDefinition(policyId);
        }

        var parts = policyId.split(String.valueOf(XRoadId.ENCODED_ID_SEPARATOR));
        if (parts.length < AssetMapper.SERVICE_ID_PARTS_WITH_VERSION) {
            log.trace("findById policyId={} too few parts={}, returning null", policyId, parts.length);
            return null;
        }

        var result = tryDecodeAndMatch(parts, AssetMapper.SERVICE_ID_PARTS_WITH_VERSION, policyId);
        if (result != null) {
            log.trace("findById policyId={} found (6-part serviceId)", policyId);
            return result;
        }
        result = tryDecodeAndMatch(parts, AssetMapper.SERVICE_ID_PARTS_WITHOUT_VERSION, policyId);
        log.trace("findById policyId={} result={}", policyId, result != null ? "found (5-part serviceId)" : "not found");
        return result;
    }

    /**
     * The SYSTEM-context policy {@code policyId} names, if one is published there — either the
     * owner-only synthetic form, or the unrestricted form for a real, SYSTEM-eligible management
     * service (accessible to any federation member, not just the owner).
     */
    @Nullable
    private PolicyDefinition findSystemPolicyDefinition(String policyId) {
        var ownerOnlyServiceId = serviceContextResolver.resolveSystemOwnerOnlyService(policyId);
        if (ownerOnlyServiceId != null) {
            if (serverConfProvider.serviceExists(ownerOnlyServiceId)
                    && serverConfProvider.getDisabledNotice(ownerOnlyServiceId) != null) {
                log.trace("findById policyId={} SYSTEM-eligible but disabled", policyId);
                return null;
            }
            return policyMapper.toOwnerOnlyPolicyDefinition(policyId, ownerOnlyServiceId.getClientId(), contextIds.system());
        }
        var systemServiceId = serviceContextResolver.resolveSystemService(policyId);
        if (systemServiceId == null) {
            log.trace("findById policyId={} not published under SYSTEM", policyId);
            return null;
        }
        if (serverConfProvider.serviceExists(systemServiceId) && serverConfProvider.getDisabledNotice(systemServiceId) != null) {
            log.trace("findById policyId={} SYSTEM-eligible but disabled", policyId);
            return null;
        }
        if (!serverConfProvider.getServiceAccessRights(systemServiceId).isEmpty()) {
            // Access rights are configured: this plain, unrestricted id must not resolve — the
            // per-subject compound id (collectPoliciesForService's system-scoped entry) is the
            // one that carries the actual grant.
            log.trace("findById policyId={} has configured access rights, plain SYSTEM id not granted", policyId);
            return null;
        }
        return toBuiltinPolicyDefinition(policyId, contextIds.system());
    }

    /** The management-context owner-only policy {@code policyId} names, if this server serves it. */
    @Nullable
    private PolicyDefinition findOwnerOnlyPolicyDefinition(String policyId) {
        var serviceId = serviceContextResolver.resolveOwnerOnlyService(policyId);
        if (serviceId == null) {
            log.trace("findById policyId={} owner-only candidate did not resolve", policyId);
            return null;
        }
        return policyMapper.toOwnerOnlyPolicyDefinition(policyId, serviceId.getClientId(), contextIds.management());
    }

    @Override
    public Stream<PolicyDefinition> findAll(QuerySpec spec) {
        if (log.isTraceEnabled()) {
            log.trace("findAll criteria={} offset={} limit={}",
                    spec.getFilterExpression(), spec.getOffset(), spec.getLimit());
        }
        var snapshot = cache.getEnumeration(this::buildPolicyList);
        if (log.isTraceEnabled()) {
            log.trace("findAll collected={} policies before filtering", snapshot.size());
        }
        return queryEvaluator.evaluate(snapshot.stream(), spec);
    }

    private List<PolicyDefinition> buildPolicyList() {
        var policies = new ArrayList<PolicyDefinition>();
        var provisionedMemberContextIds = serviceContextResolver.provisionedMemberContextIds();
        for (var member : serverConfProvider.getMembers()) {
            for (var serviceId : serverConfProvider.getAllServices(member)) {
                collectPoliciesForService(serviceId, policies, provisionedMemberContextIds);
            }
        }
        for (var serviceId : builtinServiceCatalog.activeServiceIds()) {
            var assetId = AssetMapper.encodeAssetId(serviceId);
            // TODO drop the management-context copy with the -mgmt cutover; the SYSTEM copy replaces it
            policies.add(toBuiltinPolicyDefinition(assetId, contextIds.management()));
            policies.add(toBuiltinPolicyDefinition(assetId, contextIds.system()));
        }
        var syntheticServices = serviceContextResolver.resolveSyntheticServices();
        syntheticServices.managementEntries()
                .forEach(serviceId -> policies.add(policyMapper.toOwnerOnlyPolicyDefinition(
                        ContractDefinitionMapper.ownerOnlyPolicyId(serviceId),
                        serviceId.getClientId(), contextIds.management())));
        syntheticServices.systemEntries()
                .forEach(serviceId -> policies.add(policyMapper.toOwnerOnlyPolicyDefinition(
                        ContractDefinitionMapper.ownerOnlyPolicyId(serviceId),
                        serviceId.getClientId(), contextIds.system())));
        return policies;
    }

    @Override
    public StoreResult<PolicyDefinition> create(PolicyDefinition policy) {
        log.trace("create policyId={} read-only, returning alreadyExists", policy.getId());
        return StoreResult.alreadyExists(READ_ONLY_MESSAGE);
    }

    @Override
    public StoreResult<PolicyDefinition> update(PolicyDefinition policy) {
        log.trace("update policyId={} read-only, returning notFound", policy.getId());
        return StoreResult.notFound(READ_ONLY_MESSAGE);
    }

    @Override
    public StoreResult<PolicyDefinition> delete(String policyId) {
        log.trace("delete policyId={} read-only, returning notFound", policyId);
        return StoreResult.notFound(READ_ONLY_MESSAGE);
    }

    @Nullable
    private PolicyDefinition tryDecodeAndMatch(String[] parts, int servicePartCount, String policyId) {
        if (parts.length <= servicePartCount) {
            return null;
        }

        var assetIdStr = joinParts(parts, 0, servicePartCount);
        var serviceId = AssetMapper.decodeAssetId(assetIdStr);
        if (serviceId == null) {
            return null;
        }

        if (!serverConfProvider.serviceExists(serviceId)) {
            return null;
        }

        var subjectIdStr = joinParts(parts, servicePartCount, parts.length);
        var accessRights = serverConfProvider.getServiceAccessRights(serviceId);

        var grouped = accessRights.stream()
                .collect(Collectors.groupingBy(ar -> ar.getSubjectId().asEncodedId()));

        var matchedEntries = grouped.get(subjectIdStr);
        if (matchedEntries == null || matchedEntries.isEmpty()) {
            return null;
        }

        var endpoints = matchedEntries.stream()
                .map(AccessRight::getEndpoint)
                .toList();

        var resolvedContexts = new ArrayList<>(serviceContextResolver.resolveEnabledById(serviceId));
        if (serviceContextResolver.isSystemEligible(serviceId)) {
            resolvedContexts.add(contextIds.system());
        }
        var ctxId = ServiceContextResolver.select(resolvedContexts, requestedParticipantContext.get());
        return policyMapper.toPolicyDefinition(policyId, matchedEntries.getFirst().getSubjectId(), endpoints, ctxId);
    }

    /**
     * Emits one owner-only policy per service (referenced by the paired owner-only
     * ContractDefinition) plus one per-subject policy per ACL entry, for each context the
     * service is published under.
     */
    private void collectPoliciesForService(ServiceId serviceId, List<PolicyDefinition> policies,
                                           Set<String> provisionedMemberContextIds) {
        var ownerOnlyPolicyId = ContractDefinitionMapper.ownerOnlyPolicyId(serviceId);
        policies.add(policyMapper.toOwnerOnlyPolicyDefinition(ownerOnlyPolicyId,
                serviceId.getClientId(), contextIds.management()));

        if (serverConfProvider.getDisabledNotice(serviceId) != null) {
            return;
        }
        var systemEligible = serviceContextResolver.isSystemEligible(serviceId);
        var accessRights = serverConfProvider.getServiceAccessRights(serviceId);
        if (systemEligible && accessRights.isEmpty()) {
            // No admin-configured access rights: keep the service usable federation-wide under
            // SYSTEM, matching every other SYSTEM-published synthetic/built-in entry. Once access
            // rights ARE configured, they must gate SYSTEM the same as every other context —
            // handled below via the per-subject loop, not here.
            policies.add(toBuiltinPolicyDefinition(AssetMapper.encodeAssetId(serviceId), contextIds.system()));
        }
        if (accessRights.isEmpty()) {
            return;
        }

        var grouped = accessRights.stream()
                .collect(Collectors.groupingBy(ar -> ar.getSubjectId().asEncodedId()));

        var assetId = AssetMapper.encodeAssetId(serviceId);
        var resolvedContexts = new ArrayList<>(serviceContextResolver.resolveEnabled(serviceId, provisionedMemberContextIds));
        if (systemEligible) {
            resolvedContexts.add(contextIds.system());
        }

        for (var entry : grouped.entrySet()) {
            var subjectIdEncoded = entry.getKey();
            var subjectAccessRights = entry.getValue();
            var compoundPolicyId = assetId + XRoadId.ENCODED_ID_SEPARATOR + subjectIdEncoded;
            var endpoints = subjectAccessRights.stream()
                    .map(AccessRight::getEndpoint)
                    .toList();

            for (var ctxId : resolvedContexts) {
                policies.add(policyMapper.toPolicyDefinition(compoundPolicyId,
                        subjectAccessRights.getFirst().getSubjectId(), endpoints, ctxId));
            }
        }
    }

    private PolicyDefinition toBuiltinPolicyDefinition(String policyId, String contextId) {
        var policy = Policy.Builder.newInstance()
                .type(PolicyType.SET)
                .build();
        return PolicyDefinition.Builder.newInstance()
                .id(policyId)
                .policy(policy)
                .participantContextId(contextId)
                .build();
    }

    private static String joinParts(String[] parts, int from, int to) {
        var sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) {
                sb.append(XRoadId.ENCODED_ID_SEPARATOR);
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}

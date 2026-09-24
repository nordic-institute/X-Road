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

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.spi.constants.CoreConstants;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.jetbrains.annotations.NotNull;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Read-only {@link ContractDefinitionStore} backed by serverconf access rights.
 * Emits one definition per (asset, subject) pair with compound IDs.
 */
@Slf4j
@RequiredArgsConstructor
class ContractDefinitionServerConfStore implements ContractDefinitionStore {

    private static final String READ_ONLY_MESSAGE = "Read-only: managed by ServerConf";

    private final ServerConfProvider serverConfProvider;
    private final CatalogContextIds contextIds;
    private final BuiltinServiceCatalog builtinServiceCatalog;
    private final StoreEnumerationCache<ContractDefinition> cache;
    private final ServiceContextResolver serviceContextResolver;
    private final RequestedParticipantContext requestedParticipantContext;
    private final QueryEvaluator<ContractDefinition> queryEvaluator =
            new QueryEvaluator<>(ContractDefinition::getId, ContractDefinition::getParticipantContextId);

    @Override
    @Nullable
    public ContractDefinition findById(String definitionId) {
        var cacheKeyContext = serviceContextResolver.normalizeRequestedContext(requestedParticipantContext.get());
        return cache.findById(definitionId, cacheKeyContext, () -> findByIdInternal(definitionId));
    }

    @Nullable
    private ContractDefinition findByIdInternal(String definitionId) {
        log.trace("findById definitionId={}", definitionId);
        if (definitionId == null || definitionId.isBlank()) {
            log.trace("findById definitionId blank, returning null");
            return null;
        }
        if (!definitionId.endsWith(ContractDefinitionMapper.getContractDefinitionSuffix())) {
            log.trace("findById definitionId={} missing suffix, returning null", definitionId);
            return null;
        }
        var policyId = definitionId.substring(0,
                definitionId.length() - ContractDefinitionMapper.getContractDefinitionSuffix().length());
        if (policyId.isBlank()) {
            log.trace("findById definitionId={} blank policyId after strip, returning null", definitionId);
            return null;
        }
        var builtinServiceId = builtinServiceCatalog.findServiceId(policyId);
        if (builtinServiceId != null) {
            log.trace("findById definitionId={} matched builtin", definitionId);
            return toBuiltinContractDefinition(builtinServiceId,
                    serviceContextResolver.selectBuiltinContextId(requestedParticipantContext.get()));
        }
        var systemAddressed = serviceContextResolver.isSystemAddressed(requestedParticipantContext.get());
        if (systemAddressed) {
            var systemResult = findSystemContractDefinition(policyId);
            if (systemResult != null) {
                return systemResult;
            }
            if (policyId.endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX)) {
                log.trace("findById definitionId={} SYSTEM owner-only candidate did not resolve", definitionId);
                return null;
            }
            // Fall through: a SYSTEM-eligible real service with configured access rights
            // publishes its per-subject compound id there instead of the plain/owner-only forms.
        } else if (policyId.endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX)) {
            return findOwnerOnlyContractDefinition(policyId);
        }
        var parts = policyId.split(String.valueOf(XRoadId.ENCODED_ID_SEPARATOR));
        if (parts.length < AssetMapper.SERVICE_ID_PARTS_WITH_VERSION) {
            log.trace("findById definitionId={} too few parts={}, returning null", definitionId, parts.length);
            return null;
        }
        var result = tryDecodeAndMatch(parts, AssetMapper.SERVICE_ID_PARTS_WITH_VERSION, definitionId);
        if (result == null) {
            result = tryDecodeAndMatch(parts, AssetMapper.SERVICE_ID_PARTS_WITHOUT_VERSION, definitionId);
        }
        if (systemAddressed && result != null && !contextIds.system().equals(result.getParticipantContextId())) {
            // A SYSTEM-addressed request must never resolve to a compound id whose only match is
            // under a different context (select()'s host-context fallback) — that would grant a
            // SYSTEM-addressed lookup access it was never eligible for, mislabeled with the wrong
            // context and cached under the SYSTEM key.
            log.trace("findById definitionId={} resolved outside SYSTEM under a SYSTEM-addressed request, returning null",
                    definitionId);
            return null;
        }
        log.trace("findById definitionId={} result={}", definitionId, result != null ? "found" : "not found");
        return result;
    }

    /**
     * The SYSTEM-context definition {@code policyId} names, if one is published there — either the
     * owner-only synthetic form, or the unrestricted form for a real, SYSTEM-eligible management
     * service (accessible to any federation member, not just the owner).
     */
    @Nullable
    private ContractDefinition findSystemContractDefinition(String policyId) {
        var ownerOnlyServiceId = serviceContextResolver.resolveSystemOwnerOnlyService(policyId);
        if (ownerOnlyServiceId != null) {
            if (serverConfProvider.serviceExists(ownerOnlyServiceId)
                    && serverConfProvider.getDisabledNotice(ownerOnlyServiceId) != null) {
                log.trace("findById policyId={} SYSTEM-eligible but disabled", policyId);
                return null;
            }
            return ContractDefinitionMapper.toOwnerOnlyContractDefinition(ownerOnlyServiceId, contextIds.system());
        }
        var systemServiceId = serviceContextResolver.resolveSystemService(policyId);
        if (systemServiceId == null) {
            log.trace("findById policyId={} not published under SYSTEM", policyId);
            return null;
        }
        if (!serviceContextResolver.isSystemUnrestrictedById(systemServiceId)) {
            // Not published unrestricted under SYSTEM: never configured, disabled, or gated by
            // access rights, in which case the per-subject compound id
            // (collectContractDefinitionsForService's system-scoped entry) carries the actual grant.
            log.trace("findById policyId={} not unrestricted under SYSTEM", policyId);
            return null;
        }
        return toBuiltinContractDefinition(systemServiceId, contextIds.system());
    }

    /** The management-context owner-only definition {@code policyId} names, if this server serves it. */
    @Nullable
    private ContractDefinition findOwnerOnlyContractDefinition(String policyId) {
        var serviceId = serviceContextResolver.resolveOwnerOnlyService(policyId);
        if (serviceId == null) {
            log.trace("findById policyId={} owner-only candidate did not resolve", policyId);
            return null;
        }
        return ContractDefinitionMapper.toOwnerOnlyContractDefinition(serviceId, contextIds.management());
    }

    @Override
    @NotNull
    public Stream<ContractDefinition> findAll(QuerySpec spec) {
        if (log.isTraceEnabled()) {
            log.trace("findAll criteria={} offset={} limit={}",
                    spec.getFilterExpression(), spec.getOffset(), spec.getLimit());
        }
        var snapshot = cache.getEnumeration(this::buildContractDefinitionList);
        if (log.isTraceEnabled()) {
            log.trace("findAll collected={} definitions before filtering", snapshot.size());
        }
        return queryEvaluator.evaluate(snapshot.stream(), spec);
    }

    private List<ContractDefinition> buildContractDefinitionList() {
        var definitions = new ArrayList<ContractDefinition>();
        var provisionedMemberContextIds = serviceContextResolver.provisionedMemberContextIds();
        for (var member : serverConfProvider.getMembers()) {
            for (var serviceId : serverConfProvider.getAllServices(member)) {
                collectContractDefinitionsForService(serviceId, definitions, provisionedMemberContextIds);
            }
        }
        for (var serviceId : builtinServiceCatalog.activeServiceIds()) {
            // TODO drop the management-context copy with the -mgmt cutover; the SYSTEM copy replaces it
            definitions.add(toBuiltinContractDefinition(serviceId, contextIds.management()));
            definitions.add(toBuiltinContractDefinition(serviceId, contextIds.system()));
        }
        var syntheticServices = serviceContextResolver.resolveSyntheticServices();
        syntheticServices.managementEntries()
                .forEach(serviceId -> definitions.add(ContractDefinitionMapper.toOwnerOnlyContractDefinition(
                        serviceId, contextIds.management())));
        syntheticServices.systemEntries()
                .forEach(serviceId -> definitions.add(ContractDefinitionMapper.toOwnerOnlyContractDefinition(
                        serviceId, contextIds.system())));
        return definitions;
    }

    @Override
    public StoreResult<Void> save(ContractDefinition definition) {
        log.trace("save definitionId={} read-only, returning alreadyExists", definition.getId());
        return StoreResult.alreadyExists(READ_ONLY_MESSAGE);
    }

    @Override
    public StoreResult<Void> update(ContractDefinition definition) {
        log.trace("update definitionId={} read-only, returning notFound", definition.getId());
        return StoreResult.notFound(READ_ONLY_MESSAGE);
    }

    @Override
    public StoreResult<ContractDefinition> deleteById(String id) {
        log.trace("deleteById definitionId={} read-only, returning notFound", id);
        return StoreResult.notFound(READ_ONLY_MESSAGE);
    }

    @Nullable
    private ContractDefinition tryDecodeAndMatch(String[] parts, int servicePartCount, String definitionId) {
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
        if (serverConfProvider.getDisabledNotice(serviceId) != null) {
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
        var resolvedContexts = new ArrayList<>(serviceContextResolver.resolveEnabledById(serviceId));
        if (serviceContextResolver.isSystemEligible(serviceId)) {
            resolvedContexts.add(contextIds.system());
        }
        var ctxId = ServiceContextResolver.select(resolvedContexts, requestedParticipantContext.get());
        return ContractDefinitionMapper.toContractDefinition(serviceId, matchedEntries.getFirst().getSubjectId(), ctxId);
    }

    /**
     * Emits one owner-only definition per service (hidden from non-owner peers by EDC's
     * ContractDefinitionResolverImpl) plus one per-subject definition per ACL entry, for each
     * context the service is published under.
     */
    private void collectContractDefinitionsForService(ServiceId serviceId, List<ContractDefinition> definitions,
                                                       Set<String> provisionedMemberContextIds) {
        definitions.add(ContractDefinitionMapper.toOwnerOnlyContractDefinition(
                serviceId, contextIds.management()));
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
            definitions.add(toBuiltinContractDefinition(serviceId, contextIds.system()));
        }
        if (accessRights.isEmpty()) {
            return;
        }
        var grouped = accessRights.stream()
                .collect(Collectors.groupingBy(ar -> ar.getSubjectId().asEncodedId()));
        var resolvedContexts = new ArrayList<>(serviceContextResolver.resolveEnabled(serviceId, provisionedMemberContextIds));
        if (systemEligible) {
            resolvedContexts.add(contextIds.system());
        }

        for (var entry : grouped.entrySet()) {
            var subjectAccessRights = entry.getValue();
            for (var ctxId : resolvedContexts) {
                definitions.add(ContractDefinitionMapper.toContractDefinition(serviceId,
                        subjectAccessRights.getFirst().getSubjectId(), ctxId));
            }
        }
    }

    private ContractDefinition toBuiltinContractDefinition(ServiceId serviceId, String contextId) {
        var assetId = AssetMapper.encodeAssetId(serviceId);
        var contractId = assetId + ContractDefinitionMapper.getContractDefinitionSuffix();
        return ContractDefinition.Builder.newInstance()
                .id(contractId)
                .accessPolicyId(assetId)
                .contractPolicyId(assetId)
                .assetsSelectorCriterion(new Criterion(CoreConstants.EDC_NAMESPACE + "id", "=", assetId))
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

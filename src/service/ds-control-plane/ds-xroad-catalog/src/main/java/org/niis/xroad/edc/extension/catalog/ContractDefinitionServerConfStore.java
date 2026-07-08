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
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.ArrayList;
import java.util.List;
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
    private final GlobalConfProvider globalConfProvider;
    private final String participantContextId;
    private final String managementParticipantContextId;
    private final BuiltinServiceCatalog builtinServiceCatalog;
    private final StoreEnumerationCache<ContractDefinition> cache;
    private final QueryEvaluator<ContractDefinition> queryEvaluator =
            new QueryEvaluator<>(ContractDefinition::getId, ContractDefinition::getParticipantContextId);

    /** MANAGEMENT subsystem uses a distinct DSP identity to avoid self-negotiation constraint violations. */
    private String resolveContextId(ServiceId serviceId) {
        var mgmtService = globalConfProvider.getManagementRequestService();
        return (mgmtService != null && mgmtService.equals(serviceId.getClientId()))
                ? managementParticipantContextId
                : participantContextId;
    }

    @Override
    @Nullable
    public ContractDefinition findById(String definitionId) {
        return cache.findById(definitionId, () -> findByIdInternal(definitionId));
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
            return toBuiltinContractDefinition(builtinServiceId);
        }
        if (policyId.endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX)) {
            var assetIdStr = policyId.substring(0,
                    policyId.length() - ContractDefinitionMapper.OWNER_ONLY_SUFFIX.length());
            var ownerOnlyServiceId = AssetMapper.decodeAssetId(assetIdStr);
            if (ownerOnlyServiceId == null) {
                log.trace("findById definitionId={} owner-only candidate decode failed", definitionId);
                return null;
            }
            if (!serverConfProvider.serviceExists(ownerOnlyServiceId)
                    && !isLocallyRegisteredSubsystem(ownerOnlyServiceId.getClientId())) {
                log.trace("findById definitionId={} owner-only candidate did not resolve", definitionId);
                return null;
            }
            return ContractDefinitionMapper.toOwnerOnlyContractDefinition(
                    ownerOnlyServiceId, managementParticipantContextId);
        }
        var parts = policyId.split(String.valueOf(XRoadId.ENCODED_ID_SEPARATOR));
        if (parts.length < AssetMapper.SERVICE_ID_PARTS_WITH_VERSION) {
            log.trace("findById definitionId={} too few parts={}, returning null", definitionId, parts.length);
            return null;
        }
        var result = tryDecodeAndMatch(parts, AssetMapper.SERVICE_ID_PARTS_WITH_VERSION, definitionId);
        if (result != null) {
            log.trace("findById definitionId={} found (6-part serviceId)", definitionId);
            return result;
        }
        result = tryDecodeAndMatch(parts, AssetMapper.SERVICE_ID_PARTS_WITHOUT_VERSION, definitionId);
        log.trace("findById definitionId={} result={}", definitionId, result != null ? "found (5-part serviceId)" : "not found");
        return result;
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
        for (var member : serverConfProvider.getMembers()) {
            for (var serviceId : serverConfProvider.getAllServices(member)) {
                collectContractDefinitionsForService(serviceId, definitions);
            }
        }
        for (var serviceId : builtinServiceCatalog.activeServiceIds()) {
            definitions.add(toBuiltinContractDefinition(serviceId));
        }
        ManagementServiceCatalog.resolveSyntheticServices(globalConfProvider, serverConfProvider)
                .forEach(serviceId -> definitions.add(ContractDefinitionMapper.toOwnerOnlyContractDefinition(
                        serviceId, managementParticipantContextId)));
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
        var subjectIdStr = joinParts(parts, servicePartCount, parts.length);
        var accessRights = serverConfProvider.getServiceAccessRights(serviceId);
        var grouped = accessRights.stream()
                .collect(Collectors.groupingBy(ar -> ar.getSubjectId().asEncodedId()));
        var matchedEntries = grouped.get(subjectIdStr);
        if (matchedEntries == null || matchedEntries.isEmpty()) {
            return null;
        }
        return ContractDefinitionMapper.toContractDefinition(serviceId,
                matchedEntries.getFirst().getSubjectId(), resolveContextId(serviceId));
    }

    /**
     * Emits one owner-only definition per service (hidden from non-owner peers by EDC's
     * ContractDefinitionResolverImpl) plus one per-subject definition for each ACL entry.
     */
    private void collectContractDefinitionsForService(ServiceId serviceId,
                                                      List<ContractDefinition> definitions) {
        definitions.add(ContractDefinitionMapper.toOwnerOnlyContractDefinition(
                serviceId, managementParticipantContextId));
        if (serverConfProvider.getDisabledNotice(serviceId) != null) {
            return;
        }
        var accessRights = serverConfProvider.getServiceAccessRights(serviceId);
        if (accessRights.isEmpty()) {
            return;
        }
        var grouped = accessRights.stream()
                .collect(Collectors.groupingBy(ar -> ar.getSubjectId().asEncodedId()));

        for (var entry : grouped.entrySet()) {
            var subjectAccessRights = entry.getValue();
            definitions.add(ContractDefinitionMapper.toContractDefinition(serviceId,
                    subjectAccessRights.getFirst().getSubjectId(), resolveContextId(serviceId)));
        }
    }

    private ContractDefinition toBuiltinContractDefinition(ServiceId serviceId) {
        var assetId = AssetMapper.encodeAssetId(serviceId);
        var contractId = assetId + ContractDefinitionMapper.getContractDefinitionSuffix();
        return ContractDefinition.Builder.newInstance()
                .id(contractId)
                .accessPolicyId(assetId)
                .contractPolicyId(assetId)
                .assetsSelectorCriterion(new Criterion(CoreConstants.EDC_NAMESPACE + "id", "=", assetId))
                .participantContextId(managementParticipantContextId)
                .build();
    }

    private boolean isLocallyRegisteredSubsystem(ClientId clientId) {
        if (clientId == null || clientId.getSubsystemCode() == null) {
            return false;
        }
        try {
            var thisServer = serverConfProvider.getIdentifier();
            return thisServer != null && globalConfProvider.isSecurityServerClient(clientId, thisServer);
        } catch (Exception e) {
            log.warn("Failed to read global-conf for synthetic contract definition check '{}': {}", clientId, e.getMessage());
            return false;
        }
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

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
 * ServerConf-backed {@link ContractDefinitionStore}.
 * Enumerates one {@link ContractDefinition} per (asset, subject) pair with disambiguated IDs.
 * Write operations return {@link StoreResult} failures (read-only store).
 */
@Slf4j
class ContractDefinitionServerConfStore implements ContractDefinitionStore {

    private final ServerConfProvider serverConfProvider;
    private final GlobalConfProvider globalConfProvider;
    private final ContractDefinitionMapper contractDefinitionMapper;
    private final String participantContextId;
    private final String managementParticipantContextId;
    private final BuiltinServiceCatalog builtinServiceCatalog;
    private final QueryEvaluator<ContractDefinition> queryEvaluator =
            new QueryEvaluator<>(ContractDefinition::getId, ContractDefinition::getParticipantContextId);

    ContractDefinitionServerConfStore(ServerConfProvider serverConfProvider,
                                      GlobalConfProvider globalConfProvider,
                                      ContractDefinitionMapper contractDefinitionMapper,
                                      String participantContextId,
                                      String managementParticipantContextId,
                                      BuiltinServiceCatalog builtinServiceCatalog) {
        this.serverConfProvider = serverConfProvider;
        this.globalConfProvider = globalConfProvider;
        this.contractDefinitionMapper = contractDefinitionMapper;
        this.participantContextId = participantContextId;
        this.managementParticipantContextId = managementParticipantContextId;
        this.builtinServiceCatalog = builtinServiceCatalog;
    }

    /**
     * Chooses the participant context ID for a given serviceId.
     * MANAGEMENT subsystem uses a distinct DSP identity to avoid self-negotiation constraint violations.
     */
    private String resolveContextId(ServiceId serviceId) {
        var mgmtService = globalConfProvider.getManagementRequestService();
        return (mgmtService != null && mgmtService.equals(serviceId.getClientId()))
                ? managementParticipantContextId
                : participantContextId;
    }

    /**
     * Finds a ContractDefinition by compound definitionId ({assetId}:{subjectId}-contract-definition).
     * Strips the suffix to recover policyId, then decodes the ServiceId portion (6 or 5 parts)
     * and matches the subject from the service's access rights.
     *
     * @return the ContractDefinition, or null if not found or malformed
     */
    @Override
    @Nullable
    public ContractDefinition findById(String definitionId) {
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
            if (ownerOnlyServiceId == null
                    || !serverConfProvider.serviceExists(ownerOnlyServiceId)
                    || !serverConfProvider.getServiceAccessRights(ownerOnlyServiceId).isEmpty()) {
                log.trace("findById definitionId={} owner-only candidate did not resolve", definitionId);
                return null;
            }
            return contractDefinitionMapper.toOwnerOnlyContractDefinition(
                    ownerOnlyServiceId, resolveContextId(ownerOnlyServiceId));
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

    /**
     * Returns all ContractDefinitions across all enabled services and their access rights.
     * Iterates members x services, groups access rights by subject.
     */
    @Override
    @NotNull
    public Stream<ContractDefinition> findAll(QuerySpec spec) {
        if (log.isTraceEnabled()) {
            log.trace("findAll criteria={} offset={} limit={}",
                    spec.getFilterExpression(), spec.getOffset(), spec.getLimit());
        }
        var definitions = new ArrayList<ContractDefinition>();

        for (var member : serverConfProvider.getMembers()) {
            for (var serviceId : serverConfProvider.getAllServices(member)) {
                if (serverConfProvider.getDisabledNotice(serviceId) != null) {
                    continue;
                }
                collectContractDefinitionsForService(serviceId, definitions);
            }
        }
        for (var serviceId : builtinServiceCatalog.activeServiceIds()) {
            definitions.add(toBuiltinContractDefinition(serviceId));
        }

        if (log.isTraceEnabled()) {
            log.trace("findAll collected={} definitions before filtering", definitions.size());
        }
        return queryEvaluator.evaluate(definitions.stream(), spec);
    }

    @Override
    public StoreResult<Void> save(ContractDefinition definition) {
        log.trace("save definitionId={} read-only, returning alreadyExists", definition.getId());
        return StoreResult.alreadyExists("Read-only: managed by ServerConf");
    }

    @Override
    public StoreResult<Void> update(ContractDefinition definition) {
        log.trace("update definitionId={} read-only, returning notFound", definition.getId());
        return StoreResult.notFound("Read-only: managed by ServerConf");
    }

    @Override
    public StoreResult<ContractDefinition> deleteById(String id) {
        log.trace("deleteById definitionId={} read-only, returning notFound", id);
        return StoreResult.notFound("Read-only: managed by ServerConf");
    }

    /**
     * Attempts to decode a ServiceId from the first {@code servicePartCount} parts of the policyId,
     * then finds the matching subject from the remaining parts.
     */
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
        return contractDefinitionMapper.toContractDefinition(serviceId,
                matchedEntries.getFirst().getSubjectId(), resolveContextId(serviceId));
    }

    /**
     * Collects all ContractDefinitions for a given service.
     * <ul>
     *   <li>Non-empty ACL: one ContractDefinition per subject (legacy per-subject emission).</li>
     *   <li>Empty ACL: a single owner-only ContractDefinition referencing an owner-only
     *       accessPolicy. EDC's ContractDefinitionResolverImpl pre-evaluates the access
     *       policy at catalog time and hides the definition from non-owner peers, so the
     *       service stays invisible to external consumers while remaining reachable for
     *       the owning member's self-call (legacy proxy behaviour).</li>
     * </ul>
     */
    private void collectContractDefinitionsForService(ServiceId serviceId,
                                                      List<ContractDefinition> definitions) {
        var accessRights = serverConfProvider.getServiceAccessRights(serviceId);
        if (accessRights.isEmpty()) {
            definitions.add(contractDefinitionMapper.toOwnerOnlyContractDefinition(
                    serviceId, resolveContextId(serviceId)));
            return;
        }
        var grouped = accessRights.stream()
                .collect(Collectors.groupingBy(ar -> ar.getSubjectId().asEncodedId()));

        for (var entry : grouped.entrySet()) {
            var subjectAccessRights = entry.getValue();
            definitions.add(contractDefinitionMapper.toContractDefinition(serviceId,
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

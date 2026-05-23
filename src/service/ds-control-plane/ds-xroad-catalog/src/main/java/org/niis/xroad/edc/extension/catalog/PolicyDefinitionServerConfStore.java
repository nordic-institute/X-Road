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

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.AccessRight;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ServerConf-backed {@link PolicyDefinitionStore} that builds ODRL {@link PolicyDefinition} objects
 * from live ServerConf access rights data.
 * Each (service, subject) pair produces one PolicyDefinition with compound ID.
 * Write operations return {@link StoreResult} failures (read-only store).
 */
@Slf4j
class PolicyDefinitionServerConfStore implements PolicyDefinitionStore {

    private final ServerConfProvider serverConfProvider;
    private final GlobalConfProvider globalConfProvider;
    private final PolicyMapper policyMapper;
    private final String participantContextId;
    private final String managementParticipantContextId;
    private final BuiltinServiceCatalog builtinServiceCatalog;
    private final QueryEvaluator<PolicyDefinition> queryEvaluator =
            new QueryEvaluator<>(PolicyDefinition::getId, PolicyDefinition::getParticipantContextId);

    PolicyDefinitionServerConfStore(ServerConfProvider serverConfProvider,
                                    GlobalConfProvider globalConfProvider,
                                    PolicyMapper policyMapper,
                                    String participantContextId,
                                    String managementParticipantContextId,
                                    BuiltinServiceCatalog builtinServiceCatalog) {
        this.serverConfProvider = serverConfProvider;
        this.globalConfProvider = globalConfProvider;
        this.policyMapper = policyMapper;
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
     * Finds a PolicyDefinition by compound policyId ({assetId}:{subjectId}).
     * Decodes the ServiceId portion (5 or 6 parts), then matches the subject
     * from the service's access rights.
     *
     * @return the PolicyDefinition, or null if not found or malformed
     */
    @Override
    @Nullable
    @WithSpan("dsp-find-acl")
    public PolicyDefinition findById(@SpanAttribute String policyId) {
        log.trace("findById policyId={}", policyId);
        if (policyId == null || policyId.isBlank()) {
            log.trace("findById policyId blank, returning null");
            return null;
        }

        var builtinServiceId = builtinServiceCatalog.findServiceId(policyId);
        if (builtinServiceId != null) {
            log.trace("findById policyId={} matched builtin", policyId);
            return toBuiltinPolicyDefinition(policyId);
        }

        if (policyId.endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX)) {
            var assetIdStr = policyId.substring(0,
                    policyId.length() - ContractDefinitionMapper.OWNER_ONLY_SUFFIX.length());
            var ownerOnlyServiceId = AssetMapper.decodeAssetId(assetIdStr);
            if (ownerOnlyServiceId == null) {
                log.trace("findById policyId={} owner-only candidate decode failed", policyId);
                return null;
            }
            if (!serverConfProvider.serviceExists(ownerOnlyServiceId)
                    && !isLocallyRegisteredSubsystem(ownerOnlyServiceId.getClientId())) {
                log.trace("findById policyId={} owner-only candidate did not resolve", policyId);
                return null;
            }
            return policyMapper.toOwnerOnlyPolicyDefinition(policyId,
                    ownerOnlyServiceId.getClientId(), managementParticipantContextId);
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
     * Returns all PolicyDefinitions across all enabled services and their access rights.
     * Iterates members x services, groups access rights by subject.
     */
    @Override
    public Stream<PolicyDefinition> findAll(QuerySpec spec) {
        if (log.isTraceEnabled()) {
            log.trace("findAll criteria={} offset={} limit={}",
                    spec.getFilterExpression(), spec.getOffset(), spec.getLimit());
        }
        var policies = new ArrayList<PolicyDefinition>();

        for (var member : serverConfProvider.getMembers()) {
            for (var serviceId : serverConfProvider.getAllServices(member)) {
                collectPoliciesForService(serviceId, policies);
            }
        }
        for (var serviceId : builtinServiceCatalog.activeServiceIds()) {
            var assetId = AssetMapper.encodeAssetId(serviceId);
            policies.add(toBuiltinPolicyDefinition(assetId));
        }
        ManagementServiceCatalog.resolveSyntheticServices(globalConfProvider, serverConfProvider)
                .forEach(serviceId -> policies.add(policyMapper.toOwnerOnlyPolicyDefinition(
                        ContractDefinitionMapper.ownerOnlyPolicyId(serviceId),
                        serviceId.getClientId(), managementParticipantContextId)));

        if (log.isTraceEnabled()) {
            log.trace("findAll collected={} policies before filtering", policies.size());
        }
        return queryEvaluator.evaluate(policies.stream(), spec);
    }

    @Override
    public StoreResult<PolicyDefinition> create(PolicyDefinition policy) {
        log.trace("create policyId={} read-only, returning alreadyExists", policy.getId());
        return StoreResult.alreadyExists("Read-only: managed by ServerConf");
    }

    @Override
    public StoreResult<PolicyDefinition> update(PolicyDefinition policy) {
        log.trace("update policyId={} read-only, returning notFound", policy.getId());
        return StoreResult.notFound("Read-only: managed by ServerConf");
    }

    @Override
    public StoreResult<PolicyDefinition> delete(String policyId) {
        log.trace("delete policyId={} read-only, returning notFound", policyId);
        return StoreResult.notFound("Read-only: managed by ServerConf");
    }

    /**
     * Attempts to decode a ServiceId from the first {@code servicePartCount} parts of the policyId,
     * then finds the matching subject from remaining parts.
     */
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

        return policyMapper.toPolicyDefinition(policyId, matchedEntries.getFirst().getSubjectId(),
                endpoints, resolveContextId(serviceId));
    }

    /**
     * Collects all PolicyDefinitions for a given service.
     * <ul>
     *   <li>Non-empty ACL: one PolicyDefinition per subject.</li>
     *   <li>Empty ACL: a single owner-only PolicyDefinition. The ContractDefinition emitted
     *       by {@link ContractDefinitionServerConfStore} references this policy as its
     *       accessPolicy; EDC pre-evaluates it at catalog time and filters the
     *       ContractDefinition out for non-owner consumers.</li>
     * </ul>
     */
    private void collectPoliciesForService(ServiceId serviceId, List<PolicyDefinition> policies) {
        var ownerOnlyPolicyId = ContractDefinitionMapper.ownerOnlyPolicyId(serviceId);
        policies.add(policyMapper.toOwnerOnlyPolicyDefinition(ownerOnlyPolicyId,
                serviceId.getClientId(), managementParticipantContextId));

        if (serverConfProvider.getDisabledNotice(serviceId) != null) {
            return;
        }
        var accessRights = serverConfProvider.getServiceAccessRights(serviceId);
        if (accessRights.isEmpty()) {
            return;
        }

        var grouped = accessRights.stream()
                .collect(Collectors.groupingBy(ar -> ar.getSubjectId().asEncodedId()));

        var assetId = AssetMapper.encodeAssetId(serviceId);

        for (var entry : grouped.entrySet()) {
            var subjectIdEncoded = entry.getKey();
            var subjectAccessRights = entry.getValue();
            var compoundPolicyId = assetId + XRoadId.ENCODED_ID_SEPARATOR + subjectIdEncoded;
            var endpoints = subjectAccessRights.stream()
                    .map(AccessRight::getEndpoint)
                    .toList();

            policies.add(policyMapper.toPolicyDefinition(compoundPolicyId,
                    subjectAccessRights.getFirst().getSubjectId(), endpoints, resolveContextId(serviceId)));
        }
    }

    private PolicyDefinition toBuiltinPolicyDefinition(String policyId) {
        var policy = Policy.Builder.newInstance()
                .type(PolicyType.SET)
                .build();
        return PolicyDefinition.Builder.newInstance()
                .id(policyId)
                .policy(policy)
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
            log.warn("Failed to read global-conf for synthetic policy definition check '{}': {}", clientId, e.getMessage());
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

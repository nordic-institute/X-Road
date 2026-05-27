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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.spi.query.Criterion;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Maps X-Road (service, subject) pairs to EDC {@link ContractDefinition} instances.
 */
@Slf4j
@UtilityClass
class ContractDefinitionMapper {

    private static final String CONTRACT_DEFINITION_SUFFIX = "-contract-definition";

    /** Disjoint from per-subject {assetId}:{subjectId} IDs so lookup paths never collide. */
    static final String OWNER_ONLY_SUFFIX = "-owner-only";

    static String getContractDefinitionSuffix() {
        return CONTRACT_DEFINITION_SUFFIX;
    }

    static ContractDefinition toContractDefinition(ServiceId serviceId, XRoadId subjectId, String participantContextId) {
        if (log.isTraceEnabled()) {
            log.trace("toContractDefinition serviceId={} subjectId={}",
                    serviceId.asEncodedId(), subjectId.asEncodedId());
        }
        var assetId = AssetMapper.encodeAssetId(serviceId);
        var subjectIdStr = subjectId.asEncodedId();
        var policyId = assetId + XRoadId.ENCODED_ID_SEPARATOR + subjectIdStr;
        var contractId = policyId + CONTRACT_DEFINITION_SUFFIX;

        log.trace("toContractDefinition contractId={} policyId={} assetId={}", contractId, policyId, assetId);
        return ContractDefinition.Builder.newInstance()
                .id(contractId)
                .accessPolicyId(policyId)
                .contractPolicyId(policyId)
                .assetsSelectorCriterion(new Criterion(EDC_NAMESPACE + "id", "=", assetId))
                .participantContextId(participantContextId)
                .build();
    }

    static String ownerOnlyPolicyId(ServiceId serviceId) {
        return AssetMapper.encodeAssetId(serviceId) + OWNER_ONLY_SUFFIX;
    }

    /**
     * Emitted for services with no explicit ACL. EDC's ContractDefinitionResolverImpl
     * pre-evaluates the paired owner-only access policy at catalog time and hides the
     * definition from non-owner peers.
     */
    static ContractDefinition toOwnerOnlyContractDefinition(ServiceId serviceId, String participantContextId) {
        var assetId = AssetMapper.encodeAssetId(serviceId);
        var policyId = ownerOnlyPolicyId(serviceId);
        var contractId = policyId + CONTRACT_DEFINITION_SUFFIX;
        return ContractDefinition.Builder.newInstance()
                .id(contractId)
                .accessPolicyId(policyId)
                .contractPolicyId(policyId)
                .assetsSelectorCriterion(new Criterion(EDC_NAMESPACE + "id", "=", assetId))
                .participantContextId(participantContextId)
                .build();
    }
}

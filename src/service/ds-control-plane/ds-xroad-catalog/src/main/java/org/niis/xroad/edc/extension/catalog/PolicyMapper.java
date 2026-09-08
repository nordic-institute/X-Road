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
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.OrConstraint;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace;
import org.niis.xroad.serverconf.model.Endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps X-Road access rights to ODRL {@link PolicyDefinition} objects.
 * Constraint tree shape: AND(clientConstraint, OR(per-path ANDs)).
 */
@Slf4j
class PolicyMapper {

    static final String XROAD_CLIENT_ID_CONSTRAINT = XRoadPolicyNamespace.XROAD_CLIENT_ID;
    static final String XROAD_GLOBALGROUP_CONSTRAINT = XRoadPolicyNamespace.XROAD_GLOBAL_GROUP;
    static final String XROAD_LOCALGROUP_CONSTRAINT = XRoadPolicyNamespace.XROAD_LOCAL_GROUP;
    static final String XROAD_DATAPATH_CONSTRAINT = XRoadPolicyNamespace.XROAD_DATAPATH;
    private static final String ODRL_USE_ACTION = "http://www.w3.org/ns/odrl/2/use";

    private final ObjectMapper objectMapper = new ObjectMapper();

    PolicyDefinition toPolicyDefinition(String policyId, XRoadId subjectId,
                                        List<Endpoint> endpoints, String participantContextId) {
        if (log.isTraceEnabled()) {
            log.trace("toPolicyDefinition policyId={} subjectId={} endpointCount={}",
                    policyId, subjectId.asEncodedId(), endpoints.size());
        }
        var constraints = new ArrayList<Constraint>();
        constraints.add(createClientConstraint(subjectId));

        var specificEndpoints = endpoints.stream()
                .filter(ep -> !ep.isBaseEndpoint())
                .toList();

        if (!specificEndpoints.isEmpty()) {
            var pathConstraints = specificEndpoints.stream()
                    .map(ep -> buildPathConstraint(ep, policyId))
                    .toList();
            if (pathConstraints.size() == 1) {
                constraints.add(pathConstraints.getFirst());
            } else {
                constraints.add(OrConstraint.Builder.newInstance()
                        .constraints(pathConstraints)
                        .build());
            }
        }

        Constraint rootConstraint = constraints.size() == 1
                ? constraints.getFirst()
                : AndConstraint.Builder.newInstance()
                .constraints(constraints)
                .build();

        var permission = Permission.Builder.newInstance()
                .action(Action.Builder.newInstance().type(ODRL_USE_ACTION).build())
                .constraint(rootConstraint)
                .build();

        var policy = Policy.Builder.newInstance()
                .type(PolicyType.SET)
                .permission(permission)
                .build();

        if (log.isTraceEnabled()) {
            log.trace("toPolicyDefinition policyId={} built constraints={} specificEndpoints={}",
                    policyId, constraints.size(), specificEndpoints.size());
        }
        return PolicyDefinition.Builder.newInstance()
                .id(policyId)
                .policy(policy)
                .participantContextId(participantContextId)
                .build();
    }

    /**
     * Emitted for services with no explicit ACL. Single permission requires consumer
     * clientId to memberEquals the owner; EDC's ContractDefinitionResolverImpl pre-evaluates
     * this at catalog time and filters the definition out for non-owner consumers, preserving
     * the legacy "empty ACL means no external access" semantics.
     */
    PolicyDefinition toOwnerOnlyPolicyDefinition(String policyId, ClientId ownerMemberId,
                                                 String participantContextId) {
        if (log.isTraceEnabled()) {
            log.trace("toOwnerOnlyPolicyDefinition policyId={} ownerMember={}",
                    policyId, ownerMemberId.asEncodedId());
        }
        var clientConstraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XROAD_CLIENT_ID_CONSTRAINT))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(ownerMemberId.asEncodedId()))
                .build();

        var permission = Permission.Builder.newInstance()
                .action(Action.Builder.newInstance().type(ODRL_USE_ACTION).build())
                .constraint(clientConstraint)
                .build();

        var policy = Policy.Builder.newInstance()
                .type(PolicyType.SET)
                .permission(permission)
                .build();

        return PolicyDefinition.Builder.newInstance()
                .id(policyId)
                .policy(policy)
                .participantContextId(participantContextId)
                .build();
    }

    private AtomicConstraint createClientConstraint(XRoadId subjectId) {
        String constraintKey;
        if (subjectId instanceof GlobalGroupId) {
            constraintKey = XROAD_GLOBALGROUP_CONSTRAINT;
        } else if (subjectId instanceof LocalGroupId) {
            constraintKey = XROAD_LOCALGROUP_CONSTRAINT;
        } else {
            constraintKey = XROAD_CLIENT_ID_CONSTRAINT;
        }
        if (log.isTraceEnabled()) {
            log.trace("createClientConstraint subjectId={} constraintKey={}", subjectId.asEncodedId(), constraintKey);
        }

        return AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(constraintKey))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(subjectId.asEncodedId()))
                .build();
    }

    private Constraint buildPathConstraint(Endpoint endpoint, String serviceContext) {
        log.trace("buildPathConstraint method={} path={} serviceContext={}",
                endpoint.getMethod(), endpoint.getPath(), serviceContext);
        var pathConstraints = new ArrayList<Constraint>();
        pathConstraints.add(AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XROAD_DATAPATH_CONSTRAINT))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(endpoint.getMethod() + " " + endpoint.getPath()))
                .build());

        pathConstraints.addAll(parseAdditionalConditions(null, endpoint.getMethod(),
                endpoint.getPath(), serviceContext));

        if (pathConstraints.size() == 1) {
            return pathConstraints.getFirst();
        }
        return AndConstraint.Builder.newInstance()
                .constraints(pathConstraints)
                .build();
    }

    /**
     * The Endpoint model has no additionalCondition field yet, so callers pass null today.
     * Returns an empty list on null/blank/malformed input.
     */
    List<Constraint> parseAdditionalConditions(@Nullable String additionalConditionJson,
                                               String method, String path, String serviceId) {
        if (additionalConditionJson == null || additionalConditionJson.isBlank()) {
            return List.of();
        }
        try {
            var entries = objectMapper.readValue(additionalConditionJson,
                    new TypeReference<List<Map<String, String>>>() { });
            log.trace("parseAdditionalConditions method={} path={} serviceId={} parsed={} conditions",
                    method, path, serviceId, entries.size());
            return entries.stream()
                    .map(entry -> (Constraint) AtomicConstraint.Builder.newInstance()
                            .leftExpression(new LiteralExpression(entry.get("leftExpression")))
                            .operator(Operator.valueOf(entry.get("operator")))
                            .rightExpression(new LiteralExpression(entry.get("rightExpression")))
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Skipping malformed additionalCondition for endpoint {} {} on service {}: {}",
                    method, path, serviceId, e.getMessage());
            return List.of();
        }
    }
}

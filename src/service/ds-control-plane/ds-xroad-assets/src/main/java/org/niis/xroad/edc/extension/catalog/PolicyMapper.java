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
import org.niis.xroad.serverconf.model.Endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps X-Road access rights to ODRL {@link PolicyDefinition} objects.
 * Builds AND(clientConstraint, OR(per-path ANDs)) constraint trees per D-04.
 * Package-private per D-11.
 */
@Slf4j
class PolicyMapper {

    static final String XROAD_CLIENT_ID_CONSTRAINT = "xroad:clientId";
    static final String XROAD_GLOBALGROUP_CONSTRAINT = "xroad:globalGroupMember";
    static final String XROAD_LOCALGROUP_CONSTRAINT = "xroad:localGroupMember";
    static final String XROAD_DATAPATH_CONSTRAINT = "xroad:datapath";
    private static final String ODRL_USE_ACTION = "http://www.w3.org/ns/odrl/2/use";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a {@link PolicyDefinition} for a (service, subject) pair with the given endpoints.
     *
     * @param policyId             compound policy ID ({assetId}:{subjectId})
     * @param subjectId            the access right subject (ClientId, GlobalGroupId, or LocalGroupId)
     * @param endpoints            the endpoints the subject has access to (serverconf model Endpoint)
     * @param participantContextId the participant context ID for the PolicyDefinition
     * @return the built PolicyDefinition
     */
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
            constraints.add(OrConstraint.Builder.newInstance()
                    .constraints(pathConstraints)
                    .build());
        }

        var rootConstraint = AndConstraint.Builder.newInstance()
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
     * Creates the subject-type-specific AtomicConstraint per D-05.
     * Uses instanceof dispatch to determine the constraint key.
     */
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

    /**
     * Builds a per-path constraint: AndConstraint containing the datapath AtomicConstraint
     * and any additional conditions parsed from the endpoint.
     */
    private Constraint buildPathConstraint(Endpoint endpoint, String serviceContext) {
        log.trace("buildPathConstraint method={} path={} serviceContext={}",
                endpoint.getMethod(), endpoint.getPath(), serviceContext);
        var pathConstraints = new ArrayList<Constraint>();
        pathConstraints.add(AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XROAD_DATAPATH_CONSTRAINT))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(endpoint.getMethod() + " " + endpoint.getPath()))
                .build());

        // Structural placeholder per D-09: Endpoint model has no additionalCondition field yet.
        // When the field is added, call parseAdditionalConditions here.
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
     * Parses additional conditions from a JSON string. Structural placeholder per D-09 resolution.
     * The current Endpoint model has no additionalCondition field, so this is invoked with null.
     * When the field is added, the caller will pass the actual JSON string.
     *
     * @param additionalConditionJson JSON string (nullable) containing additional constraints
     * @param method                  the endpoint HTTP method (for logging)
     * @param path                    the endpoint path (for logging)
     * @param serviceId               the service context (for logging)
     * @return list of parsed Constraints, or empty list if null/blank/malformed
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

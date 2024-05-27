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
package org.niis.xroad.proxy.edc;

import ee.ria.xroad.common.conf.serverconf.AccessRightPath;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.niis.xroad.proxy.edc.AssetsRegistrationJob.XROAD_JOB_MANAGED_PROPERTY;

@Slf4j
@UtilityClass
public class EdcPolicyDefinitionBuilder {
    private static final ObjectMapper OBJECT_MAPPER = newMapper();

    private static final String XROAD_CLIENT_ID_CONSTRAINT = "xroad:clientId";
    private static final String XROAD_DATAPATH_CONSTRAINT = "xroad:datapath";
    private static final String XROAD_GLOBALGROUP_CONSTRAINT = "xroad:globalGroupMember";

    /**
     * Create new policy definition. Each endpoint is mapper as a separate constraint, but depending on condition we could just as well map
     * to a single constraint with IN array conditin.
     */
    public static PolicyDefinition newPolicyDefinition(String assetId, XRoadId subjectId, Set<AccessRightPath> endpointPatterns) {
        var rootConstraint = AndConstraint.Builder.newInstance();
        var perPathConstraint = OrConstraint.Builder.newInstance();

        createClientConstraint(subjectId).ifPresent(rootConstraint::constraint);

        endpointPatterns.forEach(endpointPattern -> {
            var endpointConstraint = AndConstraint.Builder.newInstance()
                    .constraint(createPathCondition(endpointPattern));

            if (StringUtils.isNotBlank(endpointPattern.additionalCondition())) {
                createAdditionalConditions(endpointPattern.additionalCondition())
                        .forEach(endpointConstraint::constraint);
            }
            perPathConstraint.constraint(endpointConstraint.build());
        });

        String policyDefinitionId = "%s:%s-policyDef".formatted(assetId, subjectId.asEncodedId());

        rootConstraint.constraint(perPathConstraint.build());
        return PolicyDefinition.Builder.newInstance()
                .id(policyDefinitionId)
                .policy(Policy.Builder.newInstance()
                        .type(PolicyType.SET)
                        .permission(Permission.Builder.newInstance()
                                .action(Action.Builder.newInstance().type("http://www.w3.org/ns/odrl/2/use").build())
                                .constraint(rootConstraint.build())
                                .build())
                        .build())
                .privateProperties(Map.of(
                        XROAD_JOB_MANAGED_PROPERTY, Boolean.TRUE.toString()))
                .build();
    }

    private Constraint createPathCondition(AccessRightPath endpointPattern) {
        return AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XROAD_DATAPATH_CONSTRAINT))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(endpointPattern.path()))
                .build();
    }

    private Optional<Constraint> createClientConstraint(XRoadId subjectId) {
        AtomicConstraint clientConstraint = null;
        if (subjectId instanceof GlobalGroupId) {
            clientConstraint = AtomicConstraint.Builder.newInstance()
                    .leftExpression(new LiteralExpression(XROAD_GLOBALGROUP_CONSTRAINT))
                    .operator(Operator.EQ)
                    .rightExpression(new LiteralExpression(subjectId.asEncodedId()))
                    .build();
        } else if (subjectId instanceof LocalGroupId) {
            // todo: implement. not yet supported.
            log.warn("LocalGroupId not yet supported. Condition will be ignored.");
        } else {
            // single client id
            clientConstraint = AtomicConstraint.Builder.newInstance()
                    .leftExpression(new LiteralExpression(XROAD_CLIENT_ID_CONSTRAINT))
                    .operator(Operator.EQ)
                    .rightExpression(new LiteralExpression(subjectId.asEncodedId()))
                    .build();
        }
        return Optional.ofNullable(clientConstraint);
    }

    private List<Constraint> createAdditionalConditions(String additionalCondition) {
        try {
            return OBJECT_MAPPER.readValue(additionalCondition, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.error("Failed to parse additional condition: {}", additionalCondition, e);
            return Collections.emptyList();
        }
    }

    private static ObjectMapper newMapper() {
        var mapper = JacksonJsonLd.createObjectMapper();
        mapper.registerSubtypes(AtomicConstraint.class, LiteralExpression.class, AndConstraint.class);
        return mapper;
    }
}

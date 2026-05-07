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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;

import java.util.stream.Stream;

/**
 * Evaluates {@link QuerySpec} against a stream of {@link PolicyDefinition} instances.
 * Supports {@code id =} and {@code participantContextId =} criteria (AND-combined).
 * Unsupported criteria are skipped with WARN log per D-12.
 * Package-private — duplicated from {@link ServerConfQueryEvaluator} typed to PolicyDefinition.
 */
@Slf4j
class PolicyQueryEvaluator {

    private static final String CRITERION_ID = "id";
    private static final String CRITERION_PARTICIPANT_CONTEXT_ID = "participantContextId";
    private static final String OPERATOR_EQUALS = "=";

    private boolean sortWarningLogged = false;

    /**
     * Evaluates a QuerySpec against a stream of PolicyDefinitions.
     * Applies criterion filters (AND-combined), then paging (offset + limit).
     */
    Stream<PolicyDefinition> evaluate(Stream<PolicyDefinition> policies, QuerySpec querySpec) {
        if (log.isTraceEnabled()) {
            log.trace("evaluate criteria={} offset={} limit={}",
                    querySpec.getFilterExpression(), querySpec.getOffset(), querySpec.getLimit());
        }
        var filtered = policies;
        for (var criterion : querySpec.getFilterExpression()) {
            filtered = applyCriterion(filtered, criterion);
        }
        if (querySpec.getSortField() != null && !sortWarningLogged) {
            log.warn("Sort not supported for PolicyDefinition queries, ignoring sortField={}, sortOrder={}",
                    querySpec.getSortField(), querySpec.getSortOrder());
            sortWarningLogged = true;
        }
        return filtered.skip(querySpec.getOffset()).limit(querySpec.getLimit());
    }

    private Stream<PolicyDefinition> applyCriterion(Stream<PolicyDefinition> policies, Criterion criterion) {
        var left = String.valueOf(criterion.getOperandLeft());
        var operator = criterion.getOperator();

        if (!OPERATOR_EQUALS.equals(operator)) {
            log.warn("Unsupported operator '{}' for criterion '{}', skipping", operator, left);
            return policies;
        }

        var right = criterion.getOperandRight();
        log.trace("applyCriterion left={} operator={} right={}", left, operator, right);
        return switch (left) {
            case CRITERION_ID -> policies.filter(p -> p.getId().equals(String.valueOf(right)));
            case CRITERION_PARTICIPANT_CONTEXT_ID -> policies.filter(p ->
                    String.valueOf(right).equals(p.getParticipantContextId()));
            default -> {
                log.warn("Unsupported criterion operand '{}', skipping", left);
                yield policies;
            }
        };
    }
}

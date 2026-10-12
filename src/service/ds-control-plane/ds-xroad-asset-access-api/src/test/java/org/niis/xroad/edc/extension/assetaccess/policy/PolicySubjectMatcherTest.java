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

package org.niis.xroad.edc.extension.assetaccess.policy;

import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.Expression;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.OrConstraint;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_CLIENT_ID;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_GLOBAL_GROUP;

class PolicySubjectMatcherTest {

    private static final String CALLER_CLIENT_ID = "DEV:COM:222:SUB1";
    private static final String OTHER_CLIENT_ID = "DEV:COM:222:SUB2";

    @Test
    void namesClientReturnsTrueForEqConstraintWithCallerId() {
        var policy = policyWithConstraint(clientConstraint(Operator.EQ, CALLER_CLIENT_ID));

        assertThat(PolicySubjectMatcher.namesClient(policy, CALLER_CLIENT_ID)).isTrue();
    }

    @Test
    void namesClientReturnsFalseForEqConstraintWithOtherClientId() {
        var policy = policyWithConstraint(clientConstraint(Operator.EQ, OTHER_CLIENT_ID));

        assertThat(PolicySubjectMatcher.namesClient(policy, CALLER_CLIENT_ID)).isFalse();
    }

    @Test
    void namesClientReturnsFalseForNeqConstraintWithCallerId() {
        var policy = policyWithConstraint(clientConstraint(Operator.NEQ, CALLER_CLIENT_ID));

        assertThat(PolicySubjectMatcher.namesClient(policy, CALLER_CLIENT_ID)).isFalse();
    }

    @Test
    void namesClientReturnsFalseForInConstraintListingCallerId() {
        var constraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XROAD_CLIENT_ID))
                .operator(Operator.IN)
                .rightExpression(new LiteralExpression(List.of(CALLER_CLIENT_ID, OTHER_CLIENT_ID)))
                .build();

        var policy = policyWithConstraint(constraint);

        assertThat(PolicySubjectMatcher.namesClient(policy, CALLER_CLIENT_ID)).isFalse();
    }

    @Test
    void namesClientReturnsFalseForEqConstraintOnGlobalGroupKey() {
        var constraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XROAD_GLOBAL_GROUP))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(CALLER_CLIENT_ID))
                .build();

        var policy = policyWithConstraint(constraint);

        assertThat(PolicySubjectMatcher.namesClient(policy, CALLER_CLIENT_ID)).isFalse();
    }

    @Test
    void namesClientReturnsTrueForEqConstraintNestedInAndConstraint() {
        var andConstraint = AndConstraint.Builder.newInstance()
                .constraint(clientConstraint(Operator.EQ, CALLER_CLIENT_ID))
                .build();

        var policy = policyWithConstraint(andConstraint);

        assertThat(PolicySubjectMatcher.namesClient(policy, CALLER_CLIENT_ID)).isTrue();
    }

    @Test
    void namesClientReturnsFalseWhenPolicyHasNoPermissions() {
        var policy = Policy.Builder.newInstance().build();

        assertThat(PolicySubjectMatcher.namesClient(policy, CALLER_CLIENT_ID)).isFalse();
    }

    @Test
    void namesClientReturnsTrueWhenMatchIsInSecondPermission() {
        var nonMatching = permissionWithConstraint(clientConstraint(Operator.EQ, OTHER_CLIENT_ID));
        var matching = permissionWithConstraint(clientConstraint(Operator.EQ, CALLER_CLIENT_ID));

        var policy = Policy.Builder.newInstance()
                .permission(nonMatching)
                .permission(matching)
                .build();

        assertThat(PolicySubjectMatcher.namesClient(policy, CALLER_CLIENT_ID)).isTrue();
    }

    @Test
    void namesClientReturnsFalseWhenRightExpressionIsNotLiteral() {
        var constraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XROAD_CLIENT_ID))
                .operator(Operator.EQ)
                .rightExpression(new NonLiteralExpression())
                .build();

        var policy = policyWithConstraint(constraint);

        assertThat(PolicySubjectMatcher.namesClient(policy, CALLER_CLIENT_ID)).isFalse();
    }

    @Test
    void anyConstraintMatchesConstraintNestedTwoLevelsDeep() {
        var deepMatch = clientConstraint(Operator.EQ, CALLER_CLIENT_ID);
        var andConstraint = AndConstraint.Builder.newInstance().constraint(deepMatch).build();
        var orConstraint = OrConstraint.Builder.newInstance().constraint(andConstraint).build();

        var policy = policyWithConstraint(orConstraint);

        var matched = PolicySubjectMatcher.anyConstraint(policy,
                atomic -> PolicySubjectMatcher.isLiteral(atomic.getRightExpression(), CALLER_CLIENT_ID));

        assertThat(matched).isTrue();
    }

    @Test
    void anyConstraintReturnsFalseWhenNothingMatches() {
        var policy = policyWithConstraint(clientConstraint(Operator.EQ, CALLER_CLIENT_ID));

        var matched = PolicySubjectMatcher.anyConstraint(policy,
                atomic -> PolicySubjectMatcher.isLiteral(atomic.getRightExpression(), OTHER_CLIENT_ID));

        assertThat(matched).isFalse();
    }

    @Test
    void isLiteralReturnsTrueForEqualValue() {
        assertThat(PolicySubjectMatcher.isLiteral(new LiteralExpression(CALLER_CLIENT_ID), CALLER_CLIENT_ID)).isTrue();
    }

    @Test
    void isLiteralReturnsFalseForDifferentValue() {
        assertThat(PolicySubjectMatcher.isLiteral(new LiteralExpression(CALLER_CLIENT_ID), OTHER_CLIENT_ID)).isFalse();
    }

    private static AtomicConstraint clientConstraint(Operator operator, String clientId) {
        return AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XROAD_CLIENT_ID))
                .operator(operator)
                .rightExpression(new LiteralExpression(clientId))
                .build();
    }

    private static Policy policyWithConstraint(Constraint constraint) {
        return Policy.Builder.newInstance().permission(permissionWithConstraint(constraint)).build();
    }

    private static Permission permissionWithConstraint(Constraint constraint) {
        return Permission.Builder.newInstance()
                .action(Action.Builder.newInstance().type("use").build())
                .constraint(constraint)
                .build();
    }

    private static final class NonLiteralExpression extends Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return null;
        }
    }
}

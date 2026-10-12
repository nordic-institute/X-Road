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

import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.Expression;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.MultiplicityConstraint;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Policy;

import java.util.function.Predicate;

import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_CLIENT_ID;

/**
 * Matches an ODRL {@link Policy} against the X-Road subject it was published for, walking permission
 * constraints that may nest under {@code AndConstraint} / {@code MultiplicityConstraint}. Shared by offer
 * selection (which also falls back to a member or group subject) and agreement reuse (which matches a
 * client id or an unrestricted policy, with no member or group fallback).
 */
public final class PolicySubjectMatcher {

    private PolicySubjectMatcher() {
    }

    /**
     * Whether any permission constraint of {@code policy} is an EQ constraint that names
     * {@code encodedClientId} as the {@code XROAD_CLIENT_ID} subject.
     */
    public static boolean namesClient(Policy policy, String encodedClientId) {
        return anyConstraint(policy, atomic -> atomic.getOperator() == Operator.EQ
                && isLiteral(atomic.getLeftExpression(), XROAD_CLIENT_ID)
                && isLiteral(atomic.getRightExpression(), encodedClientId));
    }

    /**
     * Whether {@code policy} carries no permission at all, as the provider publishes for builtin and
     * unrestricted-SYSTEM services. Such a policy applies to any caller.
     */
    public static boolean isUnrestricted(Policy policy) {
        return policy.getPermissions().isEmpty();
    }

    /**
     * Whether any permission constraint of {@code policy}, including ones nested under a
     * {@link MultiplicityConstraint}, matches {@code matches}.
     */
    public static boolean anyConstraint(Policy policy, Predicate<AtomicConstraint> matches) {
        return policy.getPermissions().stream()
                .flatMap(permission -> permission.getConstraints().stream())
                .anyMatch(constraint -> anyConstraint(constraint, matches));
    }

    private static boolean anyConstraint(Constraint constraint, Predicate<AtomicConstraint> matches) {
        if (constraint instanceof AtomicConstraint atomic) {
            return matches.test(atomic);
        }
        if (constraint instanceof MultiplicityConstraint multiplicity) {
            return multiplicity.getConstraints().stream().anyMatch(child -> anyConstraint(child, matches));
        }
        return false;
    }

    public static boolean isLiteral(Expression expression, String value) {
        return expression instanceof LiteralExpression literal && value.equals(literal.getValue());
    }
}

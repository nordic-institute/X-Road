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

package org.niis.xroad.edc.extension.policy.controlplane;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.Expression;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.MultiplicityConstraint;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.OrConstraint;
import org.eclipse.edc.policy.model.Policy;
import org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_CLIENT_ID;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_DATAPATH;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_GLOBAL_GROUP;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_LOCAL_GROUP;

/**
 * Reads the policy shapes X-Road writes into DSP offers and agreements: a subject constraint
 * ({@code xroad:clientId}, {@code xroad:localGroupMember} or {@code xroad:globalGroupMember}) alone or combined
 * with {@code xroad:datapath} constraints, as {@code AND(subject, OR(paths))}.
 *
 * <p>A right operand reads the same as a plain value or as a single-element array, which is how EDC delivers
 * one value after JSON-LD expansion. Constraints with any other left operand, such as usage rules, are ignored.
 *
 * <p>{@link #read} reads the whole policy strictly, and the remaining methods answer questions about a policy
 * of any shape, looking through every nested constraint group.
 */
@Slf4j
public final class XRoadPolicyReader {

    private static final String METHOD_PATH_SEPARATOR = " ";

    private XRoadPolicyReader() {
    }

    /**
     * Reads a policy with exactly one permission whose constraints hold exactly one {@code EQ} subject
     * constraint, at the top level or inside {@code AND} groups.
     *
     * @return the shape, or empty when the policy is not of that form or names a client id that cannot be parsed
     */
    public static Optional<XRoadPolicyShape> read(Policy policy) {
        var permissions = policy.getPermissions();
        if (permissions.size() != 1) {
            log.debug("Policy has {} permissions, expected exactly 1", permissions.size());
            return Optional.empty();
        }

        var pieces = flatten(permissions.getFirst().getConstraints());
        var subjectConstraint = findSubjectConstraint(pieces);
        if (subjectConstraint == null || subjectConstraint.getOperator() != Operator.EQ) {
            return Optional.empty();
        }
        var subject = toSubject(subjectConstraint);
        if (subject == null) {
            return Optional.empty();
        }

        var pathPieces = pieces.stream().filter(piece -> piece != subjectConstraint).toList();
        return Optional.of(new XRoadPolicyShape(subject, pathPieces));
    }

    /**
     * Whether any constraint of {@code policy}, at any nesting depth, is an {@code EQ} constraint naming
     * {@code encodedClientId} as the {@code xroad:clientId} subject.
     */
    public static boolean namesClient(Policy policy, String encodedClientId) {
        return anyAtomic(policy, atomic -> atomic.getOperator() == Operator.EQ
                && isKey(atomic, XROAD_CLIENT_ID)
                && singleValue(atomic.getRightExpression()).filter(encodedClientId::equals).isPresent());
    }

    /**
     * Whether any constraint of {@code policy}, at any nesting depth, is a local or global group subject.
     */
    public static boolean namesGroup(Policy policy) {
        return anyAtomic(policy, atomic -> isKey(atomic, XROAD_LOCAL_GROUP) || isKey(atomic, XROAD_GLOBAL_GROUP));
    }

    /**
     * Whether {@code policy} carries no permission at all, as the provider publishes for builtin and
     * unrestricted-SYSTEM services. Such a policy applies to any caller.
     */
    public static boolean isUnrestricted(Policy policy) {
        return policy.getPermissions().isEmpty();
    }

    static Optional<List<PolicyEndpoint>> readEndpoints(List<Constraint> pathPieces) {
        var endpointPieces = pathPieces.stream().filter(XRoadPolicyReader::isEndpointPiece).toList();
        if (endpointPieces.isEmpty()) {
            return Optional.of(List.of());
        }
        if (endpointPieces.size() > 1) {
            return Optional.empty();
        }

        var alternatives = endpointPieces.getFirst() instanceof OrConstraint or
                ? or.getConstraints()
                : endpointPieces;
        var endpoints = new ArrayList<PolicyEndpoint>();
        for (var alternative : alternatives) {
            var endpoint = alternative instanceof AtomicConstraint atomic ? toEndpoint(atomic) : null;
            if (endpoint == null) {
                return Optional.empty();
            }
            endpoints.add(endpoint);
        }
        return Optional.of(endpoints);
    }

    /**
     * Whether a constraint beside the subject restricts endpoints. Atomic constraints on other left operands
     * and {@code OR} groups with no datapath constraint at any depth are usage rules this reader does not
     * interpret. Anything mentioning the datapath counts as an endpoint restriction, so a shape the reader cannot
     * parse is refused rather than read as no restriction at all.
     */
    private static boolean isEndpointPiece(Constraint piece) {
        if (piece instanceof AtomicConstraint atomic) {
            return mentionsDatapath(atomic);
        }
        if (piece instanceof OrConstraint or) {
            return anyAtomic(or, XRoadPolicyReader::mentionsDatapath);
        }
        return true;
    }

    /**
     * Expands every {@link AndConstraint} into its own siblings, so that the single top-level constraint
     * {@code PolicyMapper} always adds to a permission, either a lone subject constraint or
     * {@code AND(subjectConstraint, pathPiece)}, comes back as a flat list of independent pieces.
     * {@link OrConstraint} is left intact: it marks a group of alternative path constraints, not siblings.
     */
    private static List<Constraint> flatten(List<Constraint> constraints) {
        var result = new ArrayList<Constraint>();
        for (var constraint : constraints) {
            if (constraint instanceof AndConstraint andConstraint) {
                result.addAll(flatten(andConstraint.getConstraints()));
            } else {
                result.add(constraint);
            }
        }
        return result;
    }

    private static AtomicConstraint findSubjectConstraint(List<Constraint> pieces) {
        AtomicConstraint subject = null;
        for (var piece : pieces) {
            if (piece instanceof AtomicConstraint atomic && isSubjectKey(atomic)) {
                if (subject != null) {
                    return null;
                }
                subject = atomic;
            }
        }
        return subject;
    }

    private static boolean isSubjectKey(AtomicConstraint atomic) {
        return isKey(atomic, XROAD_CLIENT_ID) || isKey(atomic, XROAD_GLOBAL_GROUP) || isKey(atomic, XROAD_LOCAL_GROUP);
    }

    private static PolicySubject toSubject(AtomicConstraint subjectConstraint) {
        var value = singleValue(subjectConstraint.getRightExpression()).orElse(null);
        if (value == null) {
            return null;
        }
        if (isKey(subjectConstraint, XROAD_LOCAL_GROUP)) {
            return new PolicySubject.LocalGroup(value);
        }
        if (isKey(subjectConstraint, XROAD_GLOBAL_GROUP)) {
            return new PolicySubject.GlobalGroup(value);
        }
        try {
            return new PolicySubject.Client(PolicyContextHelper.parseClientId(value));
        } catch (RuntimeException e) {
            log.debug("Subject constraint value '{}' could not be parsed as a client id", value, e);
            return null;
        }
    }

    private static boolean mentionsDatapath(AtomicConstraint atomic) {
        return atomic.getLeftExpression() instanceof LiteralExpression literal && mentionsDatapath(literal.getValue());
    }

    private static boolean mentionsDatapath(Object value) {
        return value instanceof Collection<?> collection
                ? collection.stream().anyMatch(XRoadPolicyReader::mentionsDatapath)
                : XROAD_DATAPATH.equals(String.valueOf(value));
    }

    private static PolicyEndpoint toEndpoint(AtomicConstraint atomic) {
        if (!isKey(atomic, XROAD_DATAPATH) || atomic.getOperator() != Operator.EQ) {
            return null;
        }
        var value = singleValue(atomic.getRightExpression()).orElse(null);
        if (value == null) {
            return null;
        }
        var separatorIndex = value.indexOf(METHOD_PATH_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == value.length() - 1) {
            return null;
        }
        return new PolicyEndpoint(value.substring(0, separatorIndex), value.substring(separatorIndex + 1));
    }

    private static boolean anyAtomic(Policy policy, Predicate<AtomicConstraint> matches) {
        return policy.getPermissions().stream()
                .flatMap(permission -> permission.getConstraints().stream())
                .anyMatch(constraint -> anyAtomic(constraint, matches));
    }

    private static boolean anyAtomic(Constraint constraint, Predicate<AtomicConstraint> matches) {
        if (constraint instanceof AtomicConstraint atomic) {
            return matches.test(atomic);
        }
        if (constraint instanceof MultiplicityConstraint multiplicity) {
            return multiplicity.getConstraints().stream().anyMatch(child -> anyAtomic(child, matches));
        }
        return false;
    }

    private static boolean isKey(AtomicConstraint atomic, String key) {
        return singleValue(atomic.getLeftExpression()).filter(key::equals).isPresent();
    }

    private static Optional<String> singleValue(Expression expression) {
        if (!(expression instanceof LiteralExpression literal)) {
            return Optional.empty();
        }
        return singleValue(literal.getValue());
    }

    private static Optional<String> singleValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size() == 1 ? singleValue(collection.iterator().next()) : Optional.empty();
        }
        return Optional.ofNullable(value).map(Object::toString);
    }
}

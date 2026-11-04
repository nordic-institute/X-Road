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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;

import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.OrConstraint;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.extension.policy.controlplane.util.LocalGroupConstraintValue;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_CLIENT_ID;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_DATAPATH;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_GLOBAL_GROUP;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace.XROAD_LOCAL_GROUP;

class XRoadPolicyReaderTest {

    private static final String USAGE_RULE_KEY = "http://www.w3.org/ns/odrl/2/purpose";
    private static final ClientId.Conf SUBSYSTEM = ClientId.Conf.create("DEV", "COM", "222", "SUB1");
    private static final ClientId.Conf MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId.Conf OWNER = ClientId.Conf.create("DEV", "COM", "333", "PROV");
    private static final GlobalGroupId.Conf GLOBAL_GROUP = GlobalGroupId.Conf.create("DEV", "owners");

    @Test
    void readsSubsystemSubjectWithoutEndpoints() {
        var shape = XRoadPolicyReader.read(policy(atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId()))).orElseThrow();

        assertThat(shape.subject()).isEqualTo(new PolicySubject.Client(SUBSYSTEM));
        assertThat(((PolicySubject.Client) shape.subject()).isSubsystem()).isTrue();
        assertThat(shape.endpoints()).contains(List.of());
    }

    @Test
    void readsMemberSubjectAsNotASubsystem() {
        var shape = XRoadPolicyReader.read(policy(atomic(XROAD_CLIENT_ID, MEMBER.asEncodedId()))).orElseThrow();

        assertThat(shape.subject()).isEqualTo(new PolicySubject.Client(MEMBER));
        assertThat(((PolicySubject.Client) shape.subject()).isSubsystem()).isFalse();
    }

    @Test
    void readsLocalGroupSubjectWithOwner() {
        var value = new LocalGroupConstraintValue(OWNER, "admins");

        var shape = XRoadPolicyReader.read(policy(atomic(XROAD_LOCAL_GROUP, value.encode()))).orElseThrow();

        assertThat(shape.subject()).isEqualTo(new PolicySubject.LocalGroup(value.encode()));
        assertThat(((PolicySubject.LocalGroup) shape.subject()).decoded()).contains(value);
    }

    @Test
    void readsGlobalGroupSubject() {
        var shape = XRoadPolicyReader.read(policy(atomic(XROAD_GLOBAL_GROUP, GLOBAL_GROUP.asEncodedId()))).orElseThrow();

        assertThat(shape.subject()).isEqualTo(new PolicySubject.GlobalGroup(GLOBAL_GROUP.asEncodedId()));
        assertThat(((PolicySubject.GlobalGroup) shape.subject()).groupId()).contains(GLOBAL_GROUP);
    }

    @Test
    void groupSubjectWithUndecodableValueIsStillReadAsAGroup() {
        var local = XRoadPolicyReader.read(policy(atomic(XROAD_LOCAL_GROUP, "bareGroupCode"))).orElseThrow();
        var global = XRoadPolicyReader.read(policy(atomic(XROAD_GLOBAL_GROUP, "noColon"))).orElseThrow();

        assertThat(((PolicySubject.LocalGroup) local.subject()).decoded()).isEmpty();
        assertThat(((PolicySubject.GlobalGroup) global.subject()).groupId()).isEmpty();
    }

    @Test
    void singleAndArrayFormRightOperandsReadTheSame() {
        var single = policy(and(atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId()), atomic(XROAD_DATAPATH, "GET /a")));
        var array = policy(and(atomic(XROAD_CLIENT_ID, List.of(SUBSYSTEM.asEncodedId())),
                atomic(XROAD_DATAPATH, List.of("GET /a"))));

        assertThat(XRoadPolicyReader.read(array).orElseThrow().subject())
                .isEqualTo(XRoadPolicyReader.read(single).orElseThrow().subject());
        assertThat(XRoadPolicyReader.read(array).orElseThrow().endpoints())
                .isEqualTo(XRoadPolicyReader.read(single).orElseThrow().endpoints())
                .contains(List.of(new PolicyEndpoint("GET", "/a")));
    }

    @Test
    void arrayFormRightOperandIsMatchedByNamesClient() {
        var policy = policy(atomic(XROAD_CLIENT_ID, List.of(SUBSYSTEM.asEncodedId())));

        assertThat(XRoadPolicyReader.namesClient(policy, SUBSYSTEM.asEncodedId())).isTrue();
    }

    @Test
    void readsEndpointsOfOrGroupInPolicyOrder() {
        var paths = OrConstraint.Builder.newInstance()
                .constraint(atomic(XROAD_DATAPATH, "GET /a/b c"))
                .constraint(atomic(XROAD_DATAPATH, "POST /z"))
                .build();

        var shape = XRoadPolicyReader.read(policy(and(atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId()), paths)))
                .orElseThrow();

        assertThat(shape.endpoints()).contains(List.of(
                new PolicyEndpoint("GET", "/a/b c"), new PolicyEndpoint("POST", "/z")));
    }

    @Test
    void unknownUsageRulesAreIgnored() {
        var usageOr = OrConstraint.Builder.newInstance()
                .constraint(atomic(USAGE_RULE_KEY, "research"))
                .constraint(atomic(USAGE_RULE_KEY, "statistics"))
                .build();
        var policy = policy(and(atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId()),
                atomic(USAGE_RULE_KEY, "research"), usageOr, atomic(XROAD_DATAPATH, "GET /a")));

        var shape = XRoadPolicyReader.read(policy).orElseThrow();

        assertThat(shape.subject()).isEqualTo(new PolicySubject.Client(SUBSYSTEM));
        assertThat(shape.endpoints()).contains(List.of(new PolicyEndpoint("GET", "/a")));
    }

    @Test
    void unreadablePoliciesAreEmpty() {
        var twoPermissions = Policy.Builder.newInstance()
                .permission(permission(atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId())))
                .permission(permission(atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId())))
                .build();
        var noSubject = policy(atomic(XROAD_DATAPATH, "GET /a"));
        var twoSubjects = policy(and(atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId()),
                atomic(XROAD_GLOBAL_GROUP, GLOBAL_GROUP.asEncodedId())));
        var notEqual = policy(atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId(), Operator.NEQ));
        var malformedClient = policy(atomic(XROAD_CLIENT_ID, "not-an-encoded-client-id"));
        var multiValuedSubject = policy(atomic(XROAD_CLIENT_ID, List.of(SUBSYSTEM.asEncodedId(), MEMBER.asEncodedId())));

        assertThat(XRoadPolicyReader.read(twoPermissions)).isEmpty();
        assertThat(XRoadPolicyReader.read(noSubject)).isEmpty();
        assertThat(XRoadPolicyReader.read(twoSubjects)).isEmpty();
        assertThat(XRoadPolicyReader.read(notEqual)).isEmpty();
        assertThat(XRoadPolicyReader.read(malformedClient)).isEmpty();
        assertThat(XRoadPolicyReader.read(multiValuedSubject)).isEmpty();
    }

    @Test
    void unrecognisedEndpointShapesAreEmpty() {
        var subject = atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId());

        assertThat(endpointsOf(and(subject, atomic(XROAD_DATAPATH, "GETa")))).isEmpty();
        assertThat(endpointsOf(and(subject, atomic(XROAD_DATAPATH, "GET /a", Operator.IN)))).isEmpty();
        assertThat(endpointsOf(and(subject, atomic(XROAD_DATAPATH, "GET /a"), atomic(XROAD_DATAPATH, "GET /b")))).isEmpty();
        assertThat(endpointsOf(and(subject, OrConstraint.Builder.newInstance()
                .constraint(atomic(XROAD_DATAPATH, "GET /a"))
                .constraint(atomic(USAGE_RULE_KEY, "research"))
                .build()))).isEmpty();
    }

    @Test
    void endpointShapesNestedTooDeepAreRefusedRatherThanReadAsUnrestricted() {
        var subject = atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId());
        var perEndpointConditions = OrConstraint.Builder.newInstance()
                .constraint(and(atomic(XROAD_DATAPATH, "GET /a"), atomic(USAGE_RULE_KEY, "research")))
                .constraint(and(atomic(XROAD_DATAPATH, "POST /b"), atomic(USAGE_RULE_KEY, "research")))
                .build();
        var multiValuedDatapathKey = atomic(List.of(XROAD_DATAPATH, USAGE_RULE_KEY), "GET /a");
        var nestedDatapathKey = atomic(List.of(List.of(XROAD_DATAPATH, USAGE_RULE_KEY)), "GET /a");

        assertThat(endpointsOf(and(subject, perEndpointConditions))).isEmpty();
        assertThat(endpointsOf(and(subject, multiValuedDatapathKey))).isEmpty();
        assertThat(endpointsOf(and(subject, nestedDatapathKey))).isEmpty();
    }

    @Test
    void policyWithoutPermissionsIsUnrestricted() {
        assertThat(XRoadPolicyReader.isUnrestricted(Policy.Builder.newInstance().build())).isTrue();
        assertThat(XRoadPolicyReader.isUnrestricted(policy(atomic(XROAD_CLIENT_ID, SUBSYSTEM.asEncodedId())))).isFalse();
    }

    private static Optional<List<PolicyEndpoint>> endpointsOf(Constraint constraint) {
        return XRoadPolicyReader.read(policy(constraint)).orElseThrow().endpoints();
    }

    private static AtomicConstraint atomic(Object key, Object value) {
        return atomic(key, value, Operator.EQ);
    }

    private static AtomicConstraint atomic(Object key, Object value, Operator operator) {
        return AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(key))
                .operator(operator)
                .rightExpression(new LiteralExpression(value))
                .build();
    }

    private static AndConstraint and(Constraint... constraints) {
        return AndConstraint.Builder.newInstance().constraints(List.of(constraints)).build();
    }

    private static Policy policy(Constraint constraint) {
        return Policy.Builder.newInstance().permission(permission(constraint)).build();
    }

    private static Permission permission(Constraint constraint) {
        return Permission.Builder.newInstance()
                .action(Action.Builder.newInstance().type("use").build())
                .constraint(constraint)
                .build();
    }
}

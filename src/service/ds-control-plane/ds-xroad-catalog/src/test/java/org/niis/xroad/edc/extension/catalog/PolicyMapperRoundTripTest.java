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

import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.extension.policy.controlplane.PolicyEndpoint;
import org.niis.xroad.edc.extension.policy.controlplane.PolicySubject;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyReader;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyShape;
import org.niis.xroad.edc.extension.policy.controlplane.util.LocalGroupConstraintValue;
import org.niis.xroad.serverconf.model.Endpoint;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyMapperRoundTripTest {

    private static final String PARTICIPANT_CTX = "xroad-provider";
    private static final String POLICY_ID = "policy-1";

    private static final ClientId.Conf SERVICE_OWNER = ClientId.Conf.create("DEV", "COM", "222", "PROVIDER");
    private static final ClientId.Conf SUBSYSTEM = ClientId.Conf.create("DEV", "COM", "333", "CONSUMER");
    private static final ClientId.Conf MEMBER = ClientId.Conf.create("DEV", "COM", "333");
    private static final GlobalGroupId.Conf GLOBAL_GROUP = GlobalGroupId.Conf.create("DEV", "owners");
    private static final LocalGroupId.Conf LOCAL_GROUP = LocalGroupId.Conf.create("admins");

    private static final Endpoint GET_HELLO = new Endpoint("svc", "GET", "/api/hello", false);
    private static final Endpoint POST_HELLO = new Endpoint("svc", "POST", "/api/hello", false);
    private static final Endpoint BASE_ENDPOINT = new Endpoint("svc", "*", "**", true);

    private final PolicyMapper mapper = new PolicyMapper();

    @Test
    void subsystemSubjectRoundTrips() {
        assertThat(subjectOf(SUBSYSTEM)).isEqualTo(new PolicySubject.Client(SUBSYSTEM));
    }

    @Test
    void memberSubjectRoundTrips() {
        assertThat(subjectOf(MEMBER)).isEqualTo(new PolicySubject.Client(MEMBER));
    }

    @Test
    void globalGroupSubjectRoundTrips() {
        var subject = (PolicySubject.GlobalGroup) subjectOf(GLOBAL_GROUP);

        assertThat(subject.groupId()).contains(GLOBAL_GROUP);
    }

    @Test
    void localGroupSubjectRoundTripsWithItsOwner() {
        var subject = (PolicySubject.LocalGroup) subjectOf(LOCAL_GROUP);

        assertThat(subject.decoded()).contains(new LocalGroupConstraintValue(SERVICE_OWNER, LOCAL_GROUP.getGroupCode()));
    }

    @Test
    void noEndpointsRoundTripToNoEndpointConstraint() {
        assertThat(endpointsOf(List.of())).isEmpty();
    }

    @Test
    void singleEndpointRoundTrips() {
        assertThat(endpointsOf(List.of(GET_HELLO))).containsExactly(new PolicyEndpoint("GET", "/api/hello"));
    }

    @Test
    void severalEndpointsRoundTripInOrder() {
        assertThat(endpointsOf(List.of(GET_HELLO, POST_HELLO)))
                .containsExactly(new PolicyEndpoint("GET", "/api/hello"), new PolicyEndpoint("POST", "/api/hello"));
    }

    @Test
    void baseEndpointRoundTripsToNoEndpointConstraint() {
        assertThat(endpointsOf(List.of(BASE_ENDPOINT))).isEmpty();
        assertThat(endpointsOf(List.of(GET_HELLO, BASE_ENDPOINT))).isEmpty();
    }

    @Test
    void ownerOnlyPolicyRoundTripsToTheOwnerMember() {
        var policy = mapper.toOwnerOnlyPolicyDefinition(POLICY_ID, SERVICE_OWNER.getMemberId(), PARTICIPANT_CTX).getPolicy();

        var shape = XRoadPolicyReader.read(policy).orElseThrow();

        assertThat(shape.subject()).isEqualTo(new PolicySubject.Client(SERVICE_OWNER.getMemberId()));
        assertThat(shape.endpoints()).contains(List.of());
    }

    private PolicySubject subjectOf(XRoadId subjectId) {
        return readShape(subjectId, List.of()).subject();
    }

    private List<PolicyEndpoint> endpointsOf(List<Endpoint> endpoints) {
        return readShape(SUBSYSTEM, endpoints).endpoints().orElseThrow();
    }

    private XRoadPolicyShape readShape(XRoadId subjectId, List<Endpoint> endpoints) {
        var policy = mapper.toPolicyDefinition(POLICY_ID, SERVICE_OWNER, subjectId, endpoints, PARTICIPANT_CTX).getPolicy();
        return XRoadPolicyReader.read(policy).orElseThrow();
    }
}

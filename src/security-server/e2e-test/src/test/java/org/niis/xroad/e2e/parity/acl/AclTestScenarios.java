/*
 * The MIT License
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

package org.niis.xroad.e2e.parity.acl;

import org.niis.xroad.e2e.parity.acl.AclScenario.AddGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.DeleteGlobalGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.Expectation;
import org.niis.xroad.e2e.parity.acl.AclScenario.GlobalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.Grant;
import org.niis.xroad.e2e.parity.acl.AclScenario.GrantGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.ListClientsRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.ManagementService;
import org.niis.xroad.e2e.parity.acl.AclScenario.Refresh;
import org.niis.xroad.e2e.parity.acl.AclScenario.RemoveGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.Revoke;
import org.niis.xroad.e2e.parity.acl.AclScenario.SwapOpenApiSpec;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.niis.xroad.e2e.parity.acl.AclParticipants.CLIENT_REG;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.GLOBAL_GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.MANAGEMENT;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.MEMBER_OF_CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.OTHER_CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.OWNERS_GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.OWNER_MEMBER;
import static org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec.PETS;
import static org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec.STATUS_ONLY;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.CONSUMER_ONLY;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.allowed;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.check;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.denied;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.endpoint;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.endpointOnly;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalGroupGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalconfPhased;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalconfRow;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.management;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.monitoring;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.openApiGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phase;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phased;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.provided;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.registration;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restService;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restToGlobalGroup;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.row;
import static org.niis.xroad.e2e.parity.acl.Verdict.ACCESS_DENIED;
import static org.niis.xroad.e2e.parity.acl.Verdict.ALLOWED;

/**
 * The scenario rows the unit tests plan, execute and classify, keyed by id.
 */
@SuppressWarnings("checkstyle:linelength")
final class AclTestScenarios {

    private static final Map<String, AclScenario> BY_ID = List.of(
            phased("V10", restService(List.of()), List.of(denied("GET", "/a")), phase(new Grant(0, CONSUMER), allowed("GET", "/a"))).as("Grant added after an earlier denial"),
            phased("V11", restService(CONSUMER_ONLY, endpoint("GET", "/foo")), List.of(allowed("GET", "/a"), allowed("GET", "/foo")), phase(new Revoke(0, CONSUMER), denied("GET", "/a"), denied("GET", "/foo"))).as("Grant revoked after an earlier allow, an endpoint grant revoked with it"),
            row("V26", endpointOnly("GET", "/foo/*"), allowed("GET", "/foo/x"), allowed("GET", "/foo/"), denied("GET", "/foo/x/y"), denied("GET", "/foo")).as("GET /foo/* endpoint granted, one segment"),
            phased("V39", openApiGranted(PETS, "GET", "/pets/*"), List.of(allowed("GET", "/pets/123")), phase(List.of(new SwapOpenApiSpec(0, STATUS_ONLY), new Refresh(0)), denied("GET", "/pets/123"))).as("OpenAPI refresh removes a granted endpoint"),
            globalconfRow("V68", globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, CONSUMER_ONLY)), allowed("GET", "/a")).as("Global group granted the base endpoint, consumer subsystem is a member"),
            globalconfRow("V72", provided(restToGlobalGroup(GlobalGroupSpec.SECURITY_SERVER_OWNERS)), allowed(MEMBER_OF_CONSUMER, "GET", "/a"), denied(OTHER_CONSUMER, "GET", "/a")).as("Group security-server-owners granted, an owner and a non-owner call"),
            globalconfPhased("V74", globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, CONSUMER_ONLY)), List.of(allowed("GET", "/a")), phase(new DeleteGlobalGroup(GLOBAL_GROUP), denied("GET", "/a"))).as("Global group deleted at the Central Server after the grant"),
            globalconfPhased("V75", globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP)), List.of(denied("GET", "/a")), phase(new AddGlobalGroupMember(GLOBAL_GROUP, CONSUMER), allowed("GET", "/a")), phase(new RemoveGlobalGroupMember(GLOBAL_GROUP, CONSUMER), denied("GET", "/a"))).as("Member added to and removed from a global group at the Central Server"),
            row("V96", provided(OWNER_MEMBER), check(monitoring(MEMBER_OF_CONSUMER, SoapRequests.METRICS, null, Expectation.NONE), ACCESS_DENIED), check(monitoring(CONSUMER, SoapRequests.METRICS, null, Expectation.NONE), ACCESS_DENIED)).as("getSecurityServerMetrics is refused to a caller that is neither owner nor monitoring client"),
            row("V97", provided(OWNER_MEMBER), allowed(monitoring(CONSUMER, SoapRequests.HEALTH_DATA, CONSUMER, new Expectation(List.of("getSecurityServerHealthDataResponse", "statisticsPeriodSeconds"), List.of("serviceEvents"), List.of(), List.of()))), allowed(monitoring(CONSUMER, SoapRequests.OPERATIONAL_DATA, null, Expectation.carrying("getSecurityServerOperationalDataResponse")))).as("Health data and operational data of the owner's Security Server need no grant"),
            row("V98", provided(OWNER_MEMBER), allowed(new ListClientsRequest(Expectation.carrying("1234", "4321", "Test member", "Test client")))).as("listClients answers without a client header and lists the members of the instance"),
            phased("V106a", management(new ManagementService(CLIENT_REG, List.of(), List.of())), List.of(registration(ALLOWED)), phase(List.of(new Revoke(0, OWNERS_GROUP), new Grant(0, CONSUMER)), registration(ACCESS_DENIED))).as("Management service moved from the owners group to the owner's subsystem"),
            phased("V106b", management(new ManagementService(CLIENT_REG, List.of(OWNERS_GROUP), List.of()), new LocalGroupSpec(MANAGEMENT, GROUP, List.of(MEMBER_OF_CONSUMER))), List.of(registration(ACCESS_DENIED)), phase(new GrantGroup(0, GROUP), registration(ALLOWED))).as("Management service granted to a local group that lists the owner")
    ).stream().collect(Collectors.toMap(AclScenario::id, Function.identity()));

    private AclTestScenarios() {
    }

    static AclScenario scenario(String id) {
        return BY_ID.get(id);
    }
}

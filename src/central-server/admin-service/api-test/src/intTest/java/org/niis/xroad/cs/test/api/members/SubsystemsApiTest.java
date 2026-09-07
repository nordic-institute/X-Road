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
package org.niis.xroad.cs.test.api.members;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.openapi.model.NewSubsystemIdDto;
import org.niis.xroad.cs.openapi.model.SubsystemAddDto;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.ManagementRequestsAdminClient;
import org.niis.xroad.cs.test.api.admin.MembersAdminClient;
import org.niis.xroad.cs.test.api.admin.SecurityServersAdminClient;
import org.niis.xroad.cs.test.api.admin.SubsystemsAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@SuppressWarnings("checkstyle:magicnumber")
class SubsystemsApiTest extends CsApiTest {

    @Test
    void addNewSubsystem(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "ss01"));
        var memberId = Step.and("member seeded", () -> seeder.seedMember(session, "ss01", memberClass));
        var subsystems = new SubsystemsAdminClient(session);
        var members = new MembersAdminClient(session);

        Step.when("subsystem is added", () ->
                subsystems.addSubsystem(subsystemAddDto(memberClass, memberId.split(":")[2], "SubSys01"))
                        .statusCode(201)
                        .body("client_id.type", equalTo("SUBSYSTEM")));

        Step.then("GET subsystems of member contains the new subsystem code", () ->
                members.getMemberSubsystems(memberId)
                        .statusCode(200)
                        .body("subsystem_id.subsystem_code", hasItem("SubSys01")));
    }

    @Test
    void deleteSubsystem(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "ss02", CsBaselineSeeder.BASELINE_MEMBER_CLASS));
        var subsystemId = Step.and("subsystem seeded", () -> seeder.seedSubsystem(session, "ss02", memberId));
        var subsystemCode = subsystemId.split(":")[3];
        var subsystems = new SubsystemsAdminClient(session);
        var members = new MembersAdminClient(session);

        Step.when("subsystem is deleted", () ->
                subsystems.deleteSubsystem(subsystemId).statusCode(204));

        Step.then("subsystem is no longer in the member's subsystem list", () ->
                members.getMemberSubsystems(memberId)
                        .statusCode(200)
                        .body("subsystem_id.subsystem_code", not(hasItem(subsystemCode))));
    }

    @Test
    void unregisterSubsystem(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "ss10", CsBaselineSeeder.BASELINE_MEMBER_CLASS));
        var sub1Id = Step.and("subsystem 1 seeded", () -> seeder.seedSubsystem(session, "ss10sub1", memberId));
        var sub2Id = Step.and("subsystem 2 seeded", () -> seeder.seedSubsystem(session, "ss10sub2", memberId));
        var serverId = Step.and("security server registered", () -> seeder.seedSecurityServer(session, "ss10", memberId));
        var sub1Code = sub1Id.split(":")[3];
        var sub2Code = sub2Id.split(":")[3];
        var mgmt = new ManagementRequestsAdminClient(session);
        var servers = new SecurityServersAdminClient(session);
        var subsystems = new SubsystemsAdminClient(session);

        Step.and("sub1 registered as client of ss10 and approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(sub1Id);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            var regId = mgmt.addManagementRequest(req).statusCode(202).extract().jsonPath().getInt("id");
            mgmt.approveRequest(regId).statusCode(200);
        });

        Step.and("sub2 registered as client of ss10 and approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(sub2Id);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            var regId = mgmt.addManagementRequest(req).statusCode(202).extract().jsonPath().getInt("id");
            mgmt.approveRequest(regId).statusCode(200);
        });

        Step.then("server clients contain sub1 and sub2", () -> {
            var clientCodes = servers.getClients(serverId)
                    .statusCode(200)
                    .extract().jsonPath().getList("client_id.subsystem_code", String.class);
            org.hamcrest.MatcherAssert.assertThat(clientCodes, hasItem(sub1Code));
            org.hamcrest.MatcherAssert.assertThat(clientCodes, hasItem(sub2Code));
        });

        Step.when("sub1 is unregistered from ss10", () ->
                subsystems.unregisterSubsystem(sub1Id, serverId).statusCode(204));

        Step.then("server clients no longer contain sub1", () ->
                servers.getClients(serverId)
                        .statusCode(200)
                        .body("client_id.subsystem_code", not(hasItem(sub1Code))));

        Step.then("server clients still contain sub2", () ->
                servers.getClients(serverId)
                        .statusCode(200)
                        .body("client_id.subsystem_code", hasItem(sub2Code)));
    }

    @ParameterizedTest
    @MethodSource("invalidSubsystemAndServerIds")
    void unregisterSubsystemFailsWithInvalidIds(String subsystemId, String serverId, CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new SubsystemsAdminClient(session);

        Step.when("unregister called with invalid IDs", () ->
                client.unregisterSubsystem(subsystemId, serverId)
                        .statusCode(400));
    }

    static Stream<Arguments> invalidSubsystemAndServerIds() {
        return Stream.of(
                Arguments.of("INVALID-FORMAT", "CS:E2E:test-member:SS-X"),
                Arguments.of("TEST:CLASS", "CS:E2E:test-member:SS-X"),
                Arguments.of("TEST:CLASS:CODE", "CS:E2E:test-member:SS-X"),
                Arguments.of("CS:E2E:test-member:Subsystem-1", "INVALID-FORMAT"),
                Arguments.of("CS:E2E:test-member:Subsystem-1", "TEST:CLASS"),
                Arguments.of("CS:E2E:test-member:Subsystem-1", "TEST:CLASS:CODE"),
                Arguments.of("INVALID-FORMAT", "INVALID-FORMAT")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidSubsystemIds")
    void deleteSubsystemFailsWithInvalidIds(String subsystemId, CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new SubsystemsAdminClient(session);

        Step.when("delete called with invalid subsystem id", () ->
                client.deleteSubsystem(subsystemId)
                        .statusCode(400));
    }

    static Stream<Arguments> invalidSubsystemIds() {
        return Stream.of(
                Arguments.of("INVALID-FORMAT"),
                Arguments.of("TEST:CLASS"),
                Arguments.of("TEST:CLASS:CODE")
        );
    }

    private SubsystemAddDto subsystemAddDto(String memberClass, String memberCode, String subsystemCode) {
        var idDto = new NewSubsystemIdDto().subsystemCode(subsystemCode);
        idDto.setMemberClass(memberClass);
        idDto.setMemberCode(memberCode);
        return new SubsystemAddDto().subsystemId(idDto);
    }
}

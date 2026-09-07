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
package org.niis.xroad.cs.test.api.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.GlobalGroupCodeAndDescriptionDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.openapi.model.MembersDto;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.ClientsAdminClient;
import org.niis.xroad.cs.test.api.admin.GlobalGroupsAdminClient;
import org.niis.xroad.cs.test.api.admin.ManagementRequestsAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@SuppressWarnings("checkstyle:magicnumber")
class ClientsApiTest extends CsApiTest {

    @Test
    void clientsListPositive(CsBaselineSeeder seeder) {
        var session = Step.given("admin session", seeder::newSession);
        var mc1 = Step.given("member class 1 seeded", () -> seeder.seedMemberClass(session, "cl01a"));
        var mc2 = Step.given("member class 2 seeded", () -> seeder.seedMemberClass(session, "cl01b"));
        var mem1 = Step.given("member 1 seeded", () -> seeder.seedMember(session, "cl01a", mc1));
        Step.given("member 2 seeded", () -> seeder.seedMember(session, "cl01b", mc1));
        var mem3 = Step.given("member 3 seeded", () -> seeder.seedMember(session, "cl01c", mc2));
        var sub1 = Step.given("sub1 seeded", () -> seeder.seedSubsystem(session, "cl01sub1", mem1));
        var sub2 = Step.given("sub2 seeded", () -> seeder.seedSubsystem(session, "cl01sub2", mem1));
        var sub3 = Step.given("sub3 seeded", () -> seeder.seedSubsystem(session, "cl01sub3", mem1));
        var ss1 = Step.given("ss1 seeded", () -> seeder.seedSecurityServer(session, "cl01ss1", mem1));
        Step.given("ss2 seeded", () -> seeder.seedSecurityServer(session, "cl01ss2", mem1));
        Step.given("ss3 seeded", () -> seeder.seedSecurityServer(session, "cl01ss3", mem3));

        var mgmt = new ManagementRequestsAdminClient(session);
        Step.given("sub1 registered as client of ss1 and approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(sub1);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(ss1);
            var reqId = mgmt.addManagementRequest(req).statusCode(202).extract().jsonPath().getInt("id");
            mgmt.approveRequest(reqId).statusCode(200);
        });

        var groups = new GlobalGroupsAdminClient(session);
        var groupCode = "grp-" + mc1 + "-cl01";
        Step.given("global group created", () ->
                groups.addGlobalGroup(new GlobalGroupCodeAndDescriptionDto().code(groupCode).description("cl01 group"))
                        .statusCode(anyOf(equalTo(201), equalTo(409))));
        Step.given("sub3 added to group", () ->
                groups.addGlobalGroupMembers(groupCode, new MembersDto().items(List.of(sub3)))
                        .statusCode(anyOf(equalTo(201), equalTo(200))));

        var sub1Code = sub1.split(":")[3];
        var sub2Code = sub2.split(":")[3];
        var sub3Code = sub3.split(":")[3];

        var clients = new ClientsAdminClient(session);

        Step.when("q=mc2 finds members with member_class mc2", () ->
                clients.findClients(Map.of("q", mc2, "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1))
                        .body("clients.find { it.client_id.member_class == '" + mc2 + "' }.client_id.member_class",
                                equalTo(mc2)));

        Step.when("q=membercl01b finds member2 by code", () ->
                clients.findClients(Map.of("q", "membercl01b", "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1))
                        .body("clients.find { it.client_id.member_code == 'membercl01b' }.client_id.member_code",
                                equalTo("membercl01b")));

        Step.when("q=member name finds by name", () ->
                clients.findClients(Map.of("q", "Seeded member cl01b", "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1))
                        .body("clients.find { it.member_name == 'Seeded member cl01b' }.member_name",
                                equalTo("Seeded member cl01b")));

        Step.when("q=sub2Code finds sub2", () ->
                clients.findClients(Map.of("q", sub2Code, "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1))
                        .body("clients.find { it.client_id.subsystem_code == '" + sub2Code
                                + "' }.client_id.subsystem_code", equalTo(sub2Code)));

        Step.when("q=subsyscl01sub finds 3 or more subsystems", () ->
                clients.findClients(Map.of("q", "subsyscl01sub", "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(3)));

        Step.when("name filter finds member3 by partial name", () ->
                clients.findClients(Map.of("name", "cl01c", "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1))
                        .body("clients.find { it.member_name == 'Seeded member cl01c' }.member_name",
                                equalTo("Seeded member cl01c")));

        Step.when("instance=CS with paging returns 200", () ->
                clients.findClients(Map.of("instance", "CS", "limit", 3, "offset", 1))
                        .statusCode(200));

        Step.when("nonexistent instance returns 0 items", () ->
                clients.findClients(Map.of("instance", "NOSUCHINSTANCE-cl01", "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(0)));

        Step.when("member_class filter finds mc2 members", () ->
                clients.findClients(Map.of("member_class", mc2, "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1))
                        .body("clients.find { it.client_id.member_class == '" + mc2 + "' }.client_id.member_class",
                                equalTo(mc2)));

        Step.when("member_class + member_code filter finds member3", () ->
                clients.findClients(Map.of("member_class", mc2, "member_code", "membercl01c", "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1)));

        Step.when("cross-class subsystem combo returns 0 items", () ->
                clients.findClients(Map.of("member_class", mc2, "member_code", "membercl01c",
                        "subsystem_code", sub2Code, "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(0)));

        Step.when("correct member + subsystem filter finds sub2", () ->
                clients.findClients(Map.of("member_class", mc1, "member_code", "membercl01a",
                        "subsystem_code", sub2Code, "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1))
                        .body("clients[0].client_id.subsystem_code", equalTo(sub2Code)));

        Step.when("MEMBER type sorted asc returns results", () ->
                clients.findClients(Map.of("client_type", "MEMBER", "sort", "client_id.member_code",
                        "desc", false, "limit", 2, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1)));

        Step.when("MEMBER type sorted desc returns results", () ->
                clients.findClients(Map.of("client_type", "MEMBER", "sort", "client_id.member_code",
                        "desc", true, "limit", 2, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1)));

        Step.when("SUBSYSTEM type filter finds our 3 subsystems", () ->
                clients.findClients(Map.of("client_type", "SUBSYSTEM", "q", "subsyscl01sub", "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(3)));

        Step.when("security_server filter finds sub1 as client of ss1", () ->
                clients.findClients(Map.of("security_server", ss1, "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", greaterThanOrEqualTo(1))
                        .body("clients.find { it.client_id.subsystem_code == '" + sub1Code
                                + "' }.client_id.subsystem_code", equalTo(sub1Code)));

        Step.when("sub3 + ss1 combined filter returns 0 items", () ->
                clients.findClients(Map.of("subsystem_code", sub3Code, "security_server", ss1,
                        "limit", 25, "offset", 0))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(0)));
    }

    @ParameterizedTest
    @MethodSource("invalidClientQueryCases")
    void clientsListNegative(String description, Map<String, Object> params, String expectedErrorCode,
            CsBaselineSeeder seeder) {
        var session = Step.given("admin session", seeder::newSession);
        var clients = new ClientsAdminClient(session);

        Step.when("invalid query returns 400 with '%s'".formatted(expectedErrorCode), () ->
                clients.findClients(params)
                        .statusCode(400)
                        .body("error.code", equalTo(expectedErrorCode)));
    }

    static Stream<Arguments> invalidClientQueryCases() {
        var str256 = "a".repeat(256);
        return Stream.of(
                Arguments.of("q too long", Map.of("q", str256, "limit", 25, "offset", 0), "validation_failure"),
                Arguments.of("name too long", Map.of("name", str256, "limit", 25, "offset", 0), "validation_failure"),
                Arguments.of("instance too long",
                        Map.of("instance", str256, "limit", 25, "offset", 0), "validation_failure"),
                Arguments.of("member_class too long",
                        Map.of("member_class", str256, "limit", 25, "offset", 0), "validation_failure"),
                Arguments.of("member_code too long",
                        Map.of("member_code", str256, "limit", 25, "offset", 0), "validation_failure"),
                Arguments.of("subsystem_code too long",
                        Map.of("subsystem_code", str256, "limit", 25, "offset", 0), "validation_failure"),
                Arguments.of("invalid encoded security_server",
                        Map.of("security_server", "MISSING:SERVER", "instance", "CS", "limit", 25, "offset", 0),
                        "invalid_encoded_id"),
                Arguments.of("invalid sort property",
                        Map.of("sort", "NOTHING", "instance", "POTATO", "limit", 25, "offset", 0),
                        "invalid_sort_properties"),
                Arguments.of("negative limit",
                        Map.of("member_class", "TEST2", "limit", -1, "offset", 0),
                        "invalid_pagination_properties"),
                Arguments.of("negative offset",
                        Map.of("member_class", "TEST2", "member_code", "member", "limit", 25, "offset", -1),
                        "invalid_pagination_properties")
        );
    }
}

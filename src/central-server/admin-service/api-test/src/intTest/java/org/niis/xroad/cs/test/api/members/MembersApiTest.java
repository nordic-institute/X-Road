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
import org.niis.xroad.cs.openapi.model.MemberAddDto;
import org.niis.xroad.cs.openapi.model.MemberNameDto;
import org.niis.xroad.cs.openapi.model.NewMemberIdDto;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.MembersAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.niis.xroad.cs.test.api.CsBaselineSeeder.BASELINE_MEMBER_CLASS;
import static org.niis.xroad.cs.test.api.CsBaselineSeeder.INSTANCE_IDENTIFIER;

@SuppressWarnings("checkstyle:magicnumber")
class MembersApiTest extends CsApiTest {

    @Test
    void createNewMember(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "m01"));
        var client = new MembersAdminClient(session);

        var memberCode = "newmem01m01";
        Step.when("new member is added", () ->
                client.addMember(memberAddDto("Test Member m01", memberClass, memberCode))
                        .statusCode(201)
                        .body("member_name", equalTo("Test Member m01"))
                        .body("client_id.member_class", equalTo(memberClass))
                        .body("client_id.member_code", equalTo(memberCode)));

        Step.and("same member added again returns 409", () ->
                client.addMember(memberAddDto("Test Member m01", memberClass, memberCode))
                        .statusCode(409));

        Step.then("GET returns the created member", () -> {
            var memberId = "%s:%s:%s".formatted(INSTANCE_IDENTIFIER, memberClass, memberCode);
            client.getMember(memberId)
                    .statusCode(200)
                    .body("member_name", equalTo("Test Member m01"));
        });
    }

    @Test
    void getMemberDetails(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "m02", BASELINE_MEMBER_CLASS));
        var client = new MembersAdminClient(session);

        Step.when("GET with valid member id returns 200", () ->
                client.getMember(memberId)
                        .statusCode(200)
                        .body("client_id.instance_id", equalTo(INSTANCE_IDENTIFIER))
                        .body("client_id.member_class", equalTo(BASELINE_MEMBER_CLASS)));

        Step.and("GET with id missing part returns 400", () ->
                client.getMember("INVALID-FORMAT")
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_member_id")));

        Step.and("GET with four-part id returns 400", () ->
                client.getMember("CS:ORG:code:extra")
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_member_id")));
    }

    @Test
    void deleteMember(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "m03", BASELINE_MEMBER_CLASS));
        var client = new MembersAdminClient(session);

        Step.when("member is deleted", () ->
                client.deleteMember(memberId).statusCode(204));

        Step.then("GET returns 404", () ->
                client.getMember(memberId)
                        .statusCode(404)
                        .body("error.code", equalTo("member_not_found")));
    }

    @Test
    void updateMemberName(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "m04", BASELINE_MEMBER_CLASS));
        var client = new MembersAdminClient(session);

        Step.when("member name is updated", () ->
                client.updateMemberName(memberId, new MemberNameDto().memberName("Updated Name m04"))
                        .statusCode(200));

        Step.then("GET returns updated name", () ->
                client.getMember(memberId)
                        .statusCode(200)
                        .body("member_name", equalTo("Updated Name m04")));

        Step.and("PATCH non-existing member returns 404", () ->
                client.updateMemberName("CS:ORG:nonexistm04", new MemberNameDto().memberName("X"))
                        .statusCode(404)
                        .body("error.code", equalTo("member_not_found")));

        Step.and("PATCH malformed id returns 400", () ->
                client.updateMemberName("WRONG-ID-FORMAT", new MemberNameDto().memberName("X"))
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_member_id")));
    }

    @Test
    void ownedServersAndGlobalGroupsForNonExistingMember(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new MembersAdminClient(session);
        var nonExistingId = "CS:ORG:nonexist99999";

        Step.when("owned-servers is queried for non-existing member", () ->
                client.getOwnedServers(nonExistingId)
                        .statusCode(200)
                        .body("$", hasSize(0)));

        Step.and("global-groups is queried for non-existing member", () ->
                client.getGlobalGroups(nonExistingId)
                        .statusCode(200)
                        .body("$", hasSize(0)));
    }

    private MemberAddDto memberAddDto(String memberName, String memberClass, String memberCode) {
        return new MemberAddDto()
                .memberName(memberName)
                .memberId(new NewMemberIdDto()
                        .memberClass(memberClass)
                        .memberCode(memberCode));
    }
}

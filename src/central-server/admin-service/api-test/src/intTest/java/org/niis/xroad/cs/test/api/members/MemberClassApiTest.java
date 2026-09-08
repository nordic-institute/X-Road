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
import org.niis.xroad.cs.openapi.model.MemberClassDescriptionDto;
import org.niis.xroad.cs.openapi.model.MemberClassDto;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.MemberClassesAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@SuppressWarnings("checkstyle:magicnumber")
class MemberClassApiTest extends CsApiTest {

    @Test
    void createDuplicateMemberClassNotAllowed(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var code = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "mc01"));
        var client = new MemberClassesAdminClient(session);

        Step.when("same member class code is added again", () ->
                client.addMemberClass(new MemberClassDto().code(code).description("dup"))
                        .statusCode(409)
                        .body("error.code", equalTo("member_class_exists")));
    }

    @Test
    void createMemberClassWithInvalidDescriptionNotAllowed(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new MemberClassesAdminClient(session);

        Step.when("member class with invalid description is posted", () ->
                client.addMemberClass(new MemberClassDto().code("MCINV02").description("Invalid description$€"))
                        .statusCode(400));
    }

    @Test
    void deleteMemberClass(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var code = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "mc03"));
        var client = new MemberClassesAdminClient(session);

        Step.when("seeded member class is deleted", () ->
                client.deleteMemberClass(code).statusCode(204));

        Step.then("member class is no longer present in the list", () ->
                client.listMemberClasses()
                        .statusCode(200)
                        .body("code", not(hasItem(code))));
    }

    @Test
    void deleteMemberClassNotAllowedWhenMembersExist(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var code = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "mc04"));
        Step.and("member seeded under that class", () -> seeder.seedMember(session, "mc04", code));
        var client = new MemberClassesAdminClient(session);

        Step.when("member class in use is deleted", () ->
                client.deleteMemberClass(code)
                        .statusCode(409)
                        .body("error.code", equalTo("member_class_is_in_use")));
    }

    @Test
    void listMemberClasses(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var codeA = Step.given("first member class seeded", () -> seeder.seedMemberClass(session, "mc05a"));
        var codeB = Step.and("second member class seeded", () -> seeder.seedMemberClass(session, "mc05b"));
        var codeC = Step.and("third member class seeded", () -> seeder.seedMemberClass(session, "mc05c"));
        var client = new MemberClassesAdminClient(session);

        Step.when("GET /member-classes is called", () ->
                client.listMemberClasses()
                        .statusCode(200)
                        .body("code", hasItem(codeA))
                        .body("code", hasItem(codeB))
                        .body("code", hasItem(codeC)));
    }

    @Test
    void updateMemberClassDescription(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var code = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "mc06"));
        var client = new MemberClassesAdminClient(session);

        Step.when("description is updated", () ->
                client.updateMemberClass(code, new MemberClassDescriptionDto().description("Updated description mc06"))
                        .statusCode(200)
                        .body("description", equalTo("Updated description mc06")));
    }

    @Test
    void updateMemberClassWithInvalidDescription(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var code = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "mc07"));
        var client = new MemberClassesAdminClient(session);

        Step.when("PATCH is called with invalid description", () ->
                client.updateMemberClass(code, new MemberClassDescriptionDto().description("Invali description $€"))
                        .statusCode(400));
    }
}

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
package org.niis.xroad.cs.test.api.globalgroups;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.openapi.model.ClientTypeDto;
import org.niis.xroad.cs.openapi.model.GlobalGroupCodeAndDescriptionDto;
import org.niis.xroad.cs.openapi.model.GlobalGroupDescriptionDto;
import org.niis.xroad.cs.openapi.model.GroupMembersFilterDto;
import org.niis.xroad.cs.openapi.model.MembersDto;
import org.niis.xroad.cs.openapi.model.PagingSortingParametersDto;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.GlobalGroupsAdminClient;
import org.niis.xroad.cs.test.api.admin.MembersAdminClient;
import org.niis.xroad.cs.test.api.admin.SubsystemsAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.niis.xroad.cs.test.api.CsBaselineSeeder.INSTANCE_IDENTIFIER;

@SuppressWarnings("checkstyle:magicnumber")
class GlobalGroupsApiTest extends CsApiTest {

    private static final String OWNERS_GROUP = "security-server-owners";

    @Test
    void addGlobalGroup(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new GlobalGroupsAdminClient(session);

        Step.when("new global group is added", () ->
                client.addGlobalGroup(groupDto("gg01", "group description gg01"))
                        .statusCode(201)
                        .body("code", equalTo("gg01"))
                        .body("description", equalTo("group description gg01"))
                        .body("member_count", equalTo(0)));

        Step.and("adding same group again returns 409", () ->
                client.addGlobalGroup(groupDto("gg01", "group description gg01"))
                        .statusCode(409)
                        .body("error.code", equalTo("global_group_exists")));
    }

    @Test
    void addGlobalGroupWithInvalidDescription(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new GlobalGroupsAdminClient(session);

        Step.when("group with invalid description is added", () ->
                client.addGlobalGroup(groupDto("gg02", "invalid description$€"))
                        .statusCode(400));
    }

    @Test
    void globalGroupsList(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new GlobalGroupsAdminClient(session);
        Step.given("three groups seeded", () -> {
            client.addGlobalGroup(groupDto("gg03a", "desc a")).statusCode(201);
            client.addGlobalGroup(groupDto("gg03b", "desc b")).statusCode(201);
            client.addGlobalGroup(groupDto("gg03c", "desc c")).statusCode(201);
        });

        Step.when("global groups list is queried", () ->
                client.listGlobalGroups()
                        .statusCode(200)
                        .body("$", hasSize(greaterThanOrEqualTo(4))));
    }

    @Test
    void updateGlobalGroup(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new GlobalGroupsAdminClient(session);
        Step.given("group seeded", () ->
                client.addGlobalGroup(groupDto("gg04", "original desc")).statusCode(201));

        Step.when("description is updated", () ->
                client.updateGlobalGroupDescription("gg04", new GlobalGroupDescriptionDto().description("new description gg04"))
                        .statusCode(200));

        Step.then("GET returns updated description", () ->
                client.getGlobalGroup("gg04")
                        .statusCode(200)
                        .body("description", equalTo("new description gg04")));
    }

    @Test
    void updateGlobalGroupWithInvalidDescription(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new GlobalGroupsAdminClient(session);
        Step.given("group seeded", () ->
                client.addGlobalGroup(groupDto("gg05", "original desc")).statusCode(201));

        Step.when("PATCH with invalid description is called", () ->
                client.updateGlobalGroupDescription("gg05", new GlobalGroupDescriptionDto().description("invalid$€"))
                        .statusCode(400));
    }

    @Test
    void deleteGlobalGroup(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new GlobalGroupsAdminClient(session);
        Step.given("group seeded", () ->
                client.addGlobalGroup(groupDto("gg06", "desc")).statusCode(201));

        Step.when("group is deleted", () ->
                client.deleteGlobalGroup("gg06").statusCode(204));

        Step.then("list no longer contains the group", () ->
                client.listGlobalGroups()
                        .statusCode(200)
                        .body("code", not(hasItem("gg06"))));

        Step.and("deleting non-existing group returns 404", () ->
                client.deleteGlobalGroup("gg06-nonexistent")
                        .statusCode(404)
                        .body("error.code", equalTo("global_group_not_found")));

        Step.and("deleting security-server-owners returns 400", () ->
                client.deleteGlobalGroup(OWNERS_GROUP)
                        .statusCode(400)
                        .body("error.code", equalTo("owners_global_group_cannot_be_deleted")));
    }

    @Test
    void globalGroupFilterModelForEmptyGroup(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new GlobalGroupsAdminClient(session);
        Step.given("empty group seeded", () ->
                client.addGlobalGroup(groupDto("gg07", "desc")).statusCode(201));

        Step.when("filter model is queried", () ->
                client.getGroupMembersFilterModel("gg07")
                        .statusCode(200)
                        .body("instances", hasSize(0))
                        .body("member_classes", hasSize(0))
                        .body("codes", hasSize(0))
                        .body("subsystems", hasSize(0)));
    }

    @Test
    void globalGroupFilterModel(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        Step.given("member classes seeded", () -> {
            seeder.seedMemberClass(session, "gg08E2E");
            seeder.seedMemberClass(session, "gg08TEST");
        });
        var memberId1 = Step.and("first member seeded", () ->
                seeder.seedMember(session, "gg08m1", "mclassgg08TEST"));
        var memberId2 = Step.and("second member seeded", () ->
                seeder.seedMember(session, "gg08m2", "mclassgg08E2E"));

        Step.and("security servers registered", () -> {
            seeder.seedSecurityServer(session, "gg08ss1", memberId2);
            seeder.seedSecurityServer(session, "gg08ss2", memberId1);
        });

        var client = new GlobalGroupsAdminClient(session);

        Step.then("filter model for security-server-owners contains expected fields", () ->
                client.getGroupMembersFilterModel(OWNERS_GROUP)
                        .statusCode(200)
                        .body("instances", hasItem(INSTANCE_IDENTIFIER))
                        .body("member_classes", hasSize(greaterThanOrEqualTo(1)))
                        .body("codes", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void globalGroupMembersList(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        Step.given("member classes seeded", () -> {
            seeder.seedMemberClass(session, "gg09E2E");
            seeder.seedMemberClass(session, "gg09TEST");
        });
        var memberId1 = Step.and("m-1 seeded", () -> seeder.seedMember(session, "gg09m1", "mclassgg09TEST"));
        var memberId2 = Step.and("m-2 seeded", () -> seeder.seedMember(session, "gg09m2", "mclassgg09E2E"));
        var memberId3 = Step.and("m-3 seeded", () -> seeder.seedMember(session, "gg09m3", "mclassgg09TEST"));
        var client = new GlobalGroupsAdminClient(session);
        Step.and("isolated group seeded with the three members", () -> {
            client.addGlobalGroup(groupDto("gg09", "members list group")).statusCode(201);
            client.addGlobalGroupMembers("gg09", new MembersDto().items(List.of(memberId1, memberId2, memberId3)))
                    .statusCode(201);
        });

        record FilterCase(String q, String sortBy, Boolean desc, List<ClientTypeDto> types,
                          String instance, String memberClass, List<String> codes,
                          List<String> subsystems, Integer limit, Integer offset,
                          int itemsInPage, int total) {
        }

        var cases = List.of(
                new FilterCase(null, null, null, null, null, null, null, null, 2, 0, 2, 3),
                new FilterCase(null, null, null, null, null, null, null, null, 2, 1, 1, 3),
                new FilterCase(null, null, null, null, null, null, null, null, null, null, 3, 3),
                new FilterCase("mclassgg09E2E", "name", true, null, null, null, null, null, null, null, 1, 1),
                new FilterCase("mclassgg09TEST", null, null, null, null, null, null, null, null, null, 2, 2),
                new FilterCase(null, "created_at", false, List.of(ClientTypeDto.MEMBER), null, null, null, null, null, null, 3, 3),
                new FilterCase(null, "type", true, List.of(ClientTypeDto.SUBSYSTEM), null, null, null, null, null, null, 0, 0),
                new FilterCase(null, "code", false,
                        List.of(ClientTypeDto.MEMBER, ClientTypeDto.SUBSYSTEM), null, null, null, null, null, null, 3, 3),
                new FilterCase(null, null, null, null, INSTANCE_IDENTIFIER, null, null, null, null, null, 3, 3),
                new FilterCase(null, null, null, null, "other", null, null, null, null, null, 0, 0),
                new FilterCase(null, "class", true, null, null, "mclassgg09TEST", null, null, null, null, 2, 2),
                new FilterCase(null, null, null, null, null, "mclassgg09E2E", null, null, null, null, 1, 1),
                new FilterCase(null, null, null, null, null, "other", null, null, null, null, 0, 0),
                new FilterCase(null, null, null, null, null, null, List.of("membergg09m1"), null, null, null, 1, 1),
                new FilterCase(null, "instance", false, null, null, null, List.of("membergg09m2", "membergg09m1"), null, null, null, 2, 2),
                new FilterCase(null, null, null, null, null, null, List.of("other"), null, null, null, 0, 0),
                new FilterCase(null, "subsystem", null, null, null, null, null, List.of("other"), null, null, 0, 0),
                new FilterCase("member", null, null, List.of(ClientTypeDto.MEMBER, ClientTypeDto.SUBSYSTEM), INSTANCE_IDENTIFIER, null,
                        List.of("membergg09m2", "membergg09m1", "membergg09m3"), null, 2, 0, 2, 3)
        );

        Step.when("member list filter cases are validated", () -> {
            for (var c : cases) {
                var paging = new PagingSortingParametersDto()
                        .sort(c.sortBy())
                        .desc(c.desc());
                if (c.limit() != null) {
                    paging.limit(c.limit());
                }
                if (c.offset() != null) {
                    paging.offset(c.offset());
                }
                var filter = new GroupMembersFilterDto()
                        .query(c.q())
                        .instance(c.instance())
                        .memberClass(c.memberClass())
                        .codes(c.codes())
                        .subsystems(c.subsystems())
                        .types(c.types())
                        .pagingSorting(paging);
                client.findGlobalGroupMembers("gg09", filter)
                        .statusCode(200)
                        .body("items", hasSize(c.itemsInPage()))
                        .body("paging_metadata.total_items", equalTo(c.total()));
            }
        });
    }

    @Test
    void addMemberToGlobalGroup(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg10"));
        var memberId = Step.and("member seeded", () -> seeder.seedMember(session, "gg10", memberClass));
        var client = new GlobalGroupsAdminClient(session);
        Step.and("group seeded", () ->
                client.addGlobalGroup(groupDto("gg10", "desc")).statusCode(201));

        Step.when("member is added to group", () ->
                client.addGlobalGroupMembers("gg10", new MembersDto().items(List.of(memberId)))
                        .statusCode(201));

        Step.then("group has 1 member", () ->
                client.getGlobalGroup("gg10")
                        .statusCode(200)
                        .body("member_count", equalTo(1)));

        Step.and("member list contains 1 entry", () ->
                client.findGlobalGroupMembers("gg10", new GroupMembersFilterDto()
                        .pagingSorting(new PagingSortingParametersDto().limit(5).offset(0)))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(1)));
    }

    @Test
    void addMembersToGlobalGroup(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg11"));
        var member1 = Step.and("member1 seeded", () -> seeder.seedMember(session, "gg11m1", memberClass));
        var sub1 = Step.and("subsystem1 seeded", () -> seeder.seedSubsystem(session, "gg11s1", member1));
        var member2 = Step.and("member2 seeded", () -> seeder.seedMember(session, "gg11m2", memberClass));
        var sub2 = Step.and("subsystem2 seeded", () -> seeder.seedSubsystem(session, "gg11s2", member2));
        var sub3 = Step.and("subsystem3 seeded", () -> seeder.seedSubsystem(session, "gg11s3", member2));
        var client = new GlobalGroupsAdminClient(session);
        Step.and("group seeded", () ->
                client.addGlobalGroup(groupDto("gg11", "desc")).statusCode(201));

        Step.when("five members/subsystems are added", () ->
                client.addGlobalGroupMembers("gg11", new MembersDto().items(
                        List.of(member1, sub1, member2, sub2, sub3)))
                        .statusCode(201));

        Step.then("group has 5 members", () ->
                client.getGlobalGroup("gg11")
                        .statusCode(200)
                        .body("member_count", equalTo(5)));

        Step.and("MEMBER filter returns 2", () ->
                client.findGlobalGroupMembers("gg11", new GroupMembersFilterDto()
                        .types(List.of(ClientTypeDto.MEMBER))
                        .pagingSorting(new PagingSortingParametersDto()))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(2)));

        Step.and("SUBSYSTEM filter returns 3", () ->
                client.findGlobalGroupMembers("gg11", new GroupMembersFilterDto()
                        .types(List.of(ClientTypeDto.SUBSYSTEM))
                        .pagingSorting(new PagingSortingParametersDto()))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(3)));

        Step.and("query for member1 returns 2", () ->
                client.findGlobalGroupMembers("gg11", new GroupMembersFilterDto()
                        .query("gg11m1")
                        .pagingSorting(new PagingSortingParametersDto()))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(2)));

        Step.and("query for member2 returns 3", () ->
                client.findGlobalGroupMembers("gg11", new GroupMembersFilterDto()
                        .query("gg11m2")
                        .pagingSorting(new PagingSortingParametersDto()))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(3)));
    }

    @Test
    void addSameMembersTwiceToGlobalGroup(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg12"));
        var member1 = Step.and("member seeded", () -> seeder.seedMember(session, "gg12m1", memberClass));
        var sub1 = Step.and("subsystem1 seeded", () -> seeder.seedSubsystem(session, "gg12s1", member1));
        var sub2 = Step.and("subsystem2 seeded", () -> seeder.seedSubsystem(session, "gg12s2", member1));
        var client = new GlobalGroupsAdminClient(session);
        Step.and("group seeded", () ->
                client.addGlobalGroup(groupDto("gg12", "desc")).statusCode(201));

        Step.when("member and sub1 are added", () ->
                client.addGlobalGroupMembers("gg12", new MembersDto().items(List.of(member1, sub1)))
                        .statusCode(201));

        Step.then("group has 2 members", () ->
                client.getGlobalGroup("gg12")
                        .statusCode(200)
                        .body("member_count", equalTo(2)));

        Step.when("sub1 (duplicate) and sub2 are added", () ->
                client.addGlobalGroupMembers("gg12", new MembersDto().items(List.of(sub1, sub2)))
                        .statusCode(201));

        Step.then("group has 3 members", () ->
                client.getGlobalGroup("gg12")
                        .statusCode(200)
                        .body("member_count", equalTo(3)));
    }

    @Test
    void addingMemberToOwnerGroupIsProhibited(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg13"));
        var memberId = Step.and("member seeded", () -> seeder.seedMember(session, "gg13", memberClass));
        var client = new GlobalGroupsAdminClient(session);

        Step.when("member is added to security-server-owners", () ->
                client.addGlobalGroupMembers(OWNERS_GROUP, new MembersDto().items(List.of(memberId)))
                        .statusCode(400)
                        .body("error.code", equalTo("cannot_add_member_to_owners_group")));
    }

    @Test
    void addingNonExistingMemberFails(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg14"));
        var memberId = Step.and("member seeded", () -> seeder.seedMember(session, "gg14", memberClass));
        var subsystemId = Step.and("subsystem seeded", () -> seeder.seedSubsystem(session, "gg14s1", memberId));
        var client = new GlobalGroupsAdminClient(session);
        Step.and("group seeded", () ->
                client.addGlobalGroup(groupDto("gg14", "desc")).statusCode(201));

        Step.when("existing members are added", () ->
                client.addGlobalGroupMembers("gg14", new MembersDto().items(List.of(memberId, subsystemId)))
                        .statusCode(201));

        Step.and("non-existing member returns 404", () ->
                client.addGlobalGroupMembers("gg14", new MembersDto().items(
                        List.of(INSTANCE_IDENTIFIER + ":" + memberClass.replace("mclass", "") + ":not-found")))
                        .statusCode(404)
                        .body("error.code", equalTo("member_not_found")));

        Step.and("non-existing subsystem returns 404", () ->
                client.addGlobalGroupMembers("gg14", new MembersDto().items(
                        List.of(memberId + ":not-found")))
                        .statusCode(404)
                        .body("error.code", equalTo("subsystem_not_found")));
    }

    @Test
    void deleteGlobalGroupMemberFailsOnProtectedGroup(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg15"));
        var memberId = Step.and("member seeded", () -> seeder.seedMember(session, "gg15", memberClass));
        Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "gg15ss1", memberId));

        var client = new GlobalGroupsAdminClient(session);

        Step.when("member is deleted from security-server-owners", () ->
                client.deleteGlobalGroupMember(OWNERS_GROUP, memberId)
                        .statusCode(400)
                        .body("error.code", equalTo("owners_global_group_member_cannot_be_deleted")));
    }

    @Test
    void addAndDeleteMembersToGlobalGroup(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg16"));
        var m1 = Step.and("m1 seeded", () -> seeder.seedMember(session, "gg16m1", memberClass));
        var m2 = Step.and("m2 seeded", () -> seeder.seedMember(session, "gg16m2", memberClass));
        var m3 = Step.and("m3 seeded", () -> seeder.seedMember(session, "gg16m3", memberClass));
        var client = new GlobalGroupsAdminClient(session);
        Step.and("group seeded and members added", () -> {
            client.addGlobalGroup(groupDto("gg16", "desc")).statusCode(201);
            client.addGlobalGroupMembers("gg16", new MembersDto().items(List.of(m1, m2, m3))).statusCode(201);
        });

        Step.when("m2 is deleted", () ->
                client.deleteGlobalGroupMember("gg16", m2).statusCode(204));

        Step.then("members list does not contain m2", () ->
                client.findGlobalGroupMembers("gg16", new GroupMembersFilterDto()
                        .pagingSorting(new PagingSortingParametersDto().limit(10).offset(0)))
                        .statusCode(200)
                        .body("items.client_id.member_code", not(hasItem("membergg16m2"))));
    }

    @Test
    void globalGroupBehaviorWhenDeletingMemberSubsystems(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg17"));
        var memberId = Step.and("member seeded", () -> seeder.seedMember(session, "gg17", memberClass));
        var sub0 = Step.and("subsystem0 seeded", () -> seeder.seedSubsystem(session, "gg17s0", memberId));
        var sub1 = Step.and("subsystem1 seeded", () -> seeder.seedSubsystem(session, "gg17s1", memberId));
        var sub2 = Step.and("subsystem2 seeded", () -> seeder.seedSubsystem(session, "gg17s2", memberId));
        var client = new GlobalGroupsAdminClient(session);
        var subsystems = new SubsystemsAdminClient(session);
        var members = new MembersAdminClient(session);
        Step.and("group seeded and all members added", () -> {
            client.addGlobalGroup(groupDto("gg17", "desc")).statusCode(201);
            client.addGlobalGroupMembers("gg17", new MembersDto().items(List.of(memberId, sub0, sub1, sub2)))
                    .statusCode(201);
        });

        Step.and("group has 4 members", () ->
                client.getGlobalGroup("gg17")
                        .statusCode(200)
                        .body("member_count", equalTo(4)));

        Step.when("subsystem0 is deleted", () ->
                subsystems.deleteSubsystem(sub0).statusCode(204));

        Step.then("members list does not contain sub0", () ->
                client.findGlobalGroupMembers("gg17", new GroupMembersFilterDto()
                        .pagingSorting(new PagingSortingParametersDto().limit(10).offset(0)))
                        .statusCode(200)
                        .body("items.client_id.subsystem_code", not(hasItem("subsysgg17s0"))));

        Step.and("group has 3 members", () ->
                client.getGlobalGroup("gg17")
                        .statusCode(200)
                        .body("member_count", equalTo(3)));

        Step.when("member is deleted", () ->
                members.deleteMember(memberId).statusCode(204));

        Step.then("members list does not contain member or remaining subsystems", () -> {
            var list = client.findGlobalGroupMembers("gg17", new GroupMembersFilterDto()
                    .pagingSorting(new PagingSortingParametersDto().limit(10).offset(0)))
                    .statusCode(200);
            list.body("items.client_id.member_code", not(hasItem("membergg17")));
            list.body("items.client_id.subsystem_code", not(hasItem("subsysgg17s1")));
            list.body("items.client_id.subsystem_code", not(hasItem("subsysgg17s2")));
        });

        Step.and("group has 0 members", () ->
                client.getGlobalGroup("gg17")
                        .statusCode(200)
                        .body("member_count", equalTo(0)));
    }

    @Test
    void addAndDeleteMembersFailsDueToWrongMember(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg18"));
        var m1 = Step.and("m1 seeded", () -> seeder.seedMember(session, "gg18m1", memberClass));
        var client = new GlobalGroupsAdminClient(session);
        Step.and("group seeded and m1 added", () -> {
            client.addGlobalGroup(groupDto("gg18", "desc")).statusCode(201);
            client.addGlobalGroupMembers("gg18", new MembersDto().items(List.of(m1))).statusCode(201);
        });

        Step.when("deleting non-existing member returns 404", () ->
                client.deleteGlobalGroupMember("gg18", INSTANCE_IDENTIFIER + ":" + memberClass.replace("mclass", "") + ":m-missing")
                        .statusCode(404)
                        .body("error.code", equalTo("member_not_found")));
    }

    @Test
    void deleteGlobalGroupMemberForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberClass = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "gg19"));
        var memberId = Step.and("member seeded", () -> seeder.seedMember(session, "gg19", memberClass));
        var client = new GlobalGroupsAdminClient(session);
        Step.and("group seeded and member added", () -> {
            client.addGlobalGroup(groupDto("gg19", "desc")).statusCode(201);
            client.addGlobalGroupMembers("gg19", new MembersDto().items(List.of(memberId))).statusCode(201);
        });

        var securityOfficerClient = new GlobalGroupsAdminClient(seeder.newSecurityOfficerSession());

        Step.when("delete-member is attempted as SECURITY_OFFICER", () ->
                securityOfficerClient.deleteGlobalGroupMember("gg19", memberId)
                        .statusCode(403));
    }

    private GlobalGroupCodeAndDescriptionDto groupDto(String code, String description) {
        return new GlobalGroupCodeAndDescriptionDto().code(code).description(description);
    }
}

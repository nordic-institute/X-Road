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
package org.niis.xroad.ss.test.api.clients;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDescriptionDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MembersDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.LocalGroupsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Local-group API scenarios migrated from the legacy Cucumber system-test suite.
 *
 * <p>Each test seeds its own namespaced subsystem client so tests run independently in any order,
 * warm or cold, without interfering with each other.
 */
@DisplayName("Local-group API — add, delete, member management")
@SuppressWarnings("checkstyle:magicnumber")
class LocalGroupsTest extends SsApiTest {

    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group <group-1> is added to TestService"
    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group <group-2> is added to TestService"
    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group <group-3> is added to TestService"
    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group <aaa-1> is added to TestService"
    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group <bbb-1> is added to TestService"
    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group <yyy-1> is added to TestService"
    @Test
    @DisplayName("Local groups added to a client are persisted and visible in the group list")
    void localGroupsAddedArePersisted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clientId = seeder.seedSubsystem(session, "lg12add");
        var localGroups = new LocalGroupsAdminClient(session);

        when("local groups 'alpha-1' and 'beta-1' are added to client %s".formatted(clientId), () -> {
            localGroups.addLocalGroup(clientId, new LocalGroupAddDto("alpha-1", "desc-alpha"))
                    .statusCode(201);
            localGroups.addLocalGroup(clientId, new LocalGroupAddDto("beta-1", "desc-beta"))
                    .statusCode(201);
        });

        then("both groups appear in the local group list for the client", () -> {
            var codes = localGroups.listLocalGroups(clientId).stream()
                    .map(LocalGroupsAdminClient.LocalGroupView::code)
                    .toList();
            assertThat(codes).contains("alpha-1", "beta-1");
        });
    }

    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group is not added as it already exists"
    @Test
    @DisplayName("Adding a local group with a code that already exists is rejected")
    void duplicateLocalGroupIsRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clientId = seeder.seedSubsystem(session, "lg12dup");
        var localGroups = new LocalGroupsAdminClient(session);

        given("local group 'grp-dup' already exists on client %s".formatted(clientId), () ->
                localGroups.addLocalGroup(clientId, new LocalGroupAddDto("grp-dup", "initial-desc"))
                        .statusCode(201));

        then("adding another group with code 'grp-dup' is rejected with local_group_code_already_exists", () ->
                localGroups.addLocalGroup(clientId, new LocalGroupAddDto("grp-dup", "duplicate-desc"))
                        .statusCode(409)
                        .body("error.code", equalTo("local_group_code_already_exists")));
    }

    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group aaa-1 is deleted"
    @Test
    @DisplayName("Deleting a local group removes it from the client group list")
    void localGroupDeletedIsRemovedFromList(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clientId = seeder.seedSubsystem(session, "lg12del");
        var localGroups = new LocalGroupsAdminClient(session);

        var groupId = given("local group 'to-delete' is added to client %s".formatted(clientId), () ->
                localGroups.createLocalGroup(clientId, new LocalGroupAddDto("to-delete", "will be removed")));

        when("the local group is deleted", () ->
                localGroups.deleteLocalGroup(groupId).statusCode(204));

        then("the local group is no longer present in the client group list", () -> {
            var codes = localGroups.listLocalGroups(clientId).stream()
                    .map(LocalGroupsAdminClient.LocalGroupView::code)
                    .toList();
            assertThat(codes).doesNotContain("to-delete");
        });
    }

    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group group-1 member is removed"
    @Test
    @DisplayName("Removing a member from a local group is persisted")
    void memberRemovedFromLocalGroupIsPersisted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clientId = seeder.seedSubsystem(session, "lg12memrm");
        var localGroups = new LocalGroupsAdminClient(session);

        var memberSubsystem = seeder.seedSubsystem(session, "lg12memrmsub");

        var groupId = given("local group 'grp-memrm' with a member is set up on client %s".formatted(clientId), () -> {
            var id = localGroups.createLocalGroup(clientId, new LocalGroupAddDto("grp-memrm", "member-removal-test"));
            localGroups.addMembers(id, new MembersDto().items(List.of(memberSubsystem)))
                    .statusCode(201);
            return id;
        });

        when("the member %s is removed from the group".formatted(memberSubsystem), () ->
                localGroups.removeMembers(groupId, new MembersDto().items(List.of(memberSubsystem)))
                        .statusCode(204));

        then("the member is no longer listed in the local group", () -> {
            var memberIds = localGroups.getLocalGroup(groupId).memberIds();
            assertThat(memberIds).doesNotContain(memberSubsystem);
        });
    }

    // MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group group-1 is edited"
    @Test
    @DisplayName("Adding a member to a local group is persisted (API member-add slice)")
    void memberAddedToLocalGroupIsPersisted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clientId = seeder.seedSubsystem(session, "lg12memadd");
        var localGroups = new LocalGroupsAdminClient(session);

        var memberSubsystem1 = seeder.seedSubsystem(session, "lg12memaddsub1");
        var memberSubsystem2 = seeder.seedSubsystem(session, "lg12memaddsub2");

        var groupId = given("local group 'grp-memadd' exists on client %s".formatted(clientId), () ->
                localGroups.createLocalGroup(clientId, new LocalGroupAddDto("grp-memadd", "member-add-test")));

        and("the group description is updated to 'edited'", () ->
                localGroups.updateLocalGroup(groupId, new LocalGroupDescriptionDto("edited"))
                        .statusCode(200));

        when("members %s and %s are added to the group".formatted(memberSubsystem1, memberSubsystem2), () ->
                localGroups.addMembers(groupId, new MembersDto().items(List.of(memberSubsystem1, memberSubsystem2)))
                        .statusCode(201));

        then("both members are present in the local group", () -> {
            var memberIds = localGroups.getLocalGroup(groupId).memberIds();
            assertThat(memberIds).contains(memberSubsystem1, memberSubsystem2);
        });

        and("the updated description is persisted", () -> {
            var updated = localGroups.getLocalGroup(groupId);
            assertThat(updated.description()).isEqualTo("edited");
        });
    }
}

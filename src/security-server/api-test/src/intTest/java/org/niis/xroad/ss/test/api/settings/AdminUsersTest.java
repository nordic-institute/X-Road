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
package org.niis.xroad.ss.test.api.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.AdminUsersAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for admin-user lifecycle: create, edit roles, change password, delete.
 */
@DisplayName("Admin users — lifecycle, password, roles")
@SuppressWarnings("checkstyle:magicnumber")
class AdminUsersTest extends SsApiTest {

    private static final List<String> ALL_ROLES = List.of(
            "XROAD_SECURITY_OFFICER",
            "XROAD_REGISTRATION_OFFICER",
            "XROAD_SERVICE_ADMINISTRATOR",
            "XROAD_SYSTEM_ADMINISTRATOR",
            "XROAD_SECURITYSERVER_OBSERVER"
    );

    private static final String STRONG_PASSWORD = "T0pSecret!789";
    private static final String STRONG_PASSWORD_2 = "T0pSecret!456";

    private static String uniqueUsername(String tag) {
        return tag + Long.toString(System.nanoTime(), 36);
    }

    // MIGRATED-FROM: 0250-ss-admin-users.feature :: "User can add new admin user with all roles"
    @Test
    @DisplayName("Admin user created with all 5 roles is persisted with all roles present")
    void adminUserCreatedWithAllRolesPersists(SsBaselineSeeder seeder) {
        var username = uniqueUsername("ar");
        var users = new AdminUsersAdminClient(seeder.newSession());

        given("a new admin user is created with all 5 roles", () ->
                users.createUser(username, STRONG_PASSWORD, ALL_ROLES)
                        .statusCode(201));

        then("the user appears in the list", () ->
                assertThat(users.userExists(username)).isTrue());

        and("the user has all 5 roles", () -> {
            var roles = users.getRolesForUser(username);
            assertThat(roles).containsExactlyInAnyOrderElementsOf(ALL_ROLES);
        });

        users.deleteUser(username).statusCode(200);
    }

    // MIGRATED-FROM: 0250-ss-admin-users.feature :: "User can edit existing admin user's roles"
    @Test
    @DisplayName("Admin user roles updated to a reduced set persists the new role set")
    void adminUserRolesEditedPersists(SsBaselineSeeder seeder) {
        var username = uniqueUsername("er");
        var users = new AdminUsersAdminClient(seeder.newSession());

        given("a new admin user is created with all roles", () ->
                users.createUser(username, STRONG_PASSWORD, ALL_ROLES)
                        .statusCode(201));

        var reducedRoles = List.of("XROAD_REGISTRATION_OFFICER", "XROAD_SYSTEM_ADMINISTRATOR");

        when("the roles are updated to only Registration Officer and System Administrator", () ->
                users.updateRoles(username, reducedRoles)
                        .statusCode(200));

        then("the persisted role set matches the reduced set", () -> {
            var roles = users.getRolesForUser(username);
            assertThat(roles).containsExactlyInAnyOrderElementsOf(reducedRoles);
            assertThat(roles).doesNotContain("XROAD_SECURITY_OFFICER", "XROAD_SERVICE_ADMINISTRATOR",
                    "XROAD_SECURITYSERVER_OBSERVER");
        });

        users.deleteUser(username).statusCode(200);
    }

    // MIGRATED-FROM: 0250-ss-admin-users.feature :: "User can change other user's password"
    @Test
    @DisplayName("Sysadmin changes another user's password; old password rejected, new accepted on login")
    void sysadminChangesOtherUserPassword(SsBaselineSeeder seeder, SsApiTestContainerSetup stack) {
        var username = uniqueUsername("op");
        var uiBaseUrl = uiBaseUrl(stack);
        var users = new AdminUsersAdminClient(seeder.newSession());

        given("a new admin user is created with System Administrator role", () ->
                users.createUser(username, STRONG_PASSWORD, List.of("XROAD_SYSTEM_ADMINISTRATOR"))
                        .statusCode(201));

        when("the sysadmin changes the user's password", () ->
                users.changePassword(username, STRONG_PASSWORD, STRONG_PASSWORD_2)
                        .statusCode(200));

        then("logging in with the new password succeeds", () ->
                assertLoginSucceeds(uiBaseUrl, username, STRONG_PASSWORD_2));

        and("logging in with the old password is rejected", () ->
                assertLoginFails(uiBaseUrl, username, STRONG_PASSWORD));

        users.deleteUser(username).statusCode(200);
    }

    // MIGRATED-FROM: 0250-ss-admin-users.feature :: "User can change its own password"
    @Test
    @DisplayName("User changes their own password; old password rejected, new accepted on login")
    void userChangesOwnPassword(SsBaselineSeeder seeder, SsApiTestContainerSetup stack) {
        var username = uniqueUsername("wp");
        var uiBaseUrl = uiBaseUrl(stack);
        var sysAdminUsers = new AdminUsersAdminClient(seeder.newSession());

        given("a new admin user is created with System Administrator role", () ->
                sysAdminUsers.createUser(username, STRONG_PASSWORD, List.of("XROAD_SYSTEM_ADMINISTRATOR"))
                        .statusCode(201));

        var ownSession = new AdminApiSession(uiBaseUrl, username, STRONG_PASSWORD);
        var ownUsers = new AdminUsersAdminClient(ownSession);

        when("the user changes their own password providing the old password", () ->
                ownUsers.changePassword(username, STRONG_PASSWORD, STRONG_PASSWORD_2)
                        .statusCode(200));

        then("logging in with the new password succeeds", () ->
                assertLoginSucceeds(uiBaseUrl, username, STRONG_PASSWORD_2));

        and("logging in with the old password is rejected", () ->
                assertLoginFails(uiBaseUrl, username, STRONG_PASSWORD));

        sysAdminUsers.deleteUser(username).statusCode(200);
    }

    // MIGRATED-FROM: 0250-ss-admin-users.feature :: "User can delete existing admin user"
    @Test
    @DisplayName("Admin user deleted via API is removed from the user list")
    void adminUserDeletedIsRemovedFromList(SsBaselineSeeder seeder) {
        var username = uniqueUsername("dl");
        var users = new AdminUsersAdminClient(seeder.newSession());

        given("a new admin user is created", () ->
                users.createUser(username, STRONG_PASSWORD, List.of("XROAD_SECURITYSERVER_OBSERVER"))
                        .statusCode(201));

        and("the user appears in the list", () ->
                assertThat(users.userExists(username)).isTrue());

        when("the user is deleted", () ->
                users.deleteUser(username).statusCode(200));

        then("the user is absent from the list", () ->
                assertThat(users.userExists(username)).isFalse());
    }

    // MIGRATED-FROM: 0250-ss-admin-users.feature :: "Add necessary admin users for other dependant tests"
    @Test
    @DisplayName("One admin user per role can be created via API with distinct namespaced usernames")
    void perRoleAdminUsersCreatedViaApi(SsBaselineSeeder seeder) {
        var users = new AdminUsersAdminClient(seeder.newSession());

        var roleUserPairs = List.of(
                new RoleUserPair("XROAD_SECURITY_OFFICER", uniqueUsername("sec")),
                new RoleUserPair("XROAD_REGISTRATION_OFFICER", uniqueUsername("reg")),
                new RoleUserPair("XROAD_SERVICE_ADMINISTRATOR", uniqueUsername("ser")),
                new RoleUserPair("XROAD_SYSTEM_ADMINISTRATOR", uniqueUsername("sys")),
                new RoleUserPair("XROAD_SECURITYSERVER_OBSERVER", uniqueUsername("obs"))
        );

        given("one admin user per role is created via API", () -> {
            for (var pair : roleUserPairs) {
                users.createUser(pair.username(), STRONG_PASSWORD, List.of(pair.role()))
                        .statusCode(201);
            }
        });

        then("each user exists with the expected single role", () -> {
            for (var pair : roleUserPairs) {
                assertThat(users.userExists(pair.username())).isTrue();
                assertThat(users.getRolesForUser(pair.username())).containsExactly(pair.role());
            }
        });

        for (var pair : roleUserPairs) {
            users.deleteUser(pair.username()).statusCode(200);
        }
    }

    @Test
    @DisplayName("Creating an admin user with a weak password is rejected with user_weak_password")
    void createUserWithWeakPasswordIsRejected(SsBaselineSeeder seeder) {
        var username = uniqueUsername("wp");
        var users = new AdminUsersAdminClient(seeder.newSession());

        then("POST /users with a password that fails the strength policy is rejected with 400 user_weak_password", () ->
                users.createUser(username, "weakpass", List.of("XROAD_SECURITYSERVER_OBSERVER"))
                        .statusCode(400)
                        .body("error.code", equalTo("user_weak_password")));
    }

    @Test
    @DisplayName("Creating an admin user with an illegal-character password is rejected with user_password_invalid_characters")
    void createUserWithIllegalCharPasswordIsRejected(SsBaselineSeeder seeder) {
        var username = uniqueUsername("ic");
        var users = new AdminUsersAdminClient(seeder.newSession());

        then("POST /users with a password containing a non-ASCII character is rejected with 400 user_password_invalid_characters", () ->
                users.createUser(username, "T0pSecret!789", List.of("XROAD_SECURITYSERVER_OBSERVER"))
                        .statusCode(400)
                        .body("error.code", equalTo("user_password_invalid_characters")));
    }

    private static String uiBaseUrl(SsApiTestContainerSetup stack) {
        var mapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        return "https://%s:%d".formatted(mapping.host(), mapping.port());
    }

    private static void assertLoginSucceeds(String uiBaseUrl, String username, String password) {
        RestAssuredFactory.givenSilent()
                .formParam("username", username)
                .formParam("password", password)
                .post(uiBaseUrl + "/login")
                .then()
                .statusCode(200);
    }

    private static void assertLoginFails(String uiBaseUrl, String username, String password) {
        RestAssuredFactory.givenSilent()
                .formParam("username", username)
                .formParam("password", password)
                .post(uiBaseUrl + "/login")
                .then()
                .statusCode(401);
    }

    private record RoleUserPair(String role, String username) {
    }
}

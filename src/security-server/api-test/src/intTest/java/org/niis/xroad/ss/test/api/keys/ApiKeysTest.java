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
package org.niis.xroad.ss.test.api.keys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminUsersAdminClient;
import org.niis.xroad.ss.test.api.admin.ApiKeysAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for API key lifecycle: create, revoke, and edit operations.
 */
@DisplayName("API key lifecycle — create, revoke, edit")
@SuppressWarnings("checkstyle:magicnumber")
class ApiKeysTest extends SsApiTest {

    private static final List<String> ALL_ROLES = List.of(
            "XROAD_SECURITY_OFFICER",
            "XROAD_REGISTRATION_OFFICER",
            "XROAD_SERVICE_ADMINISTRATOR",
            "XROAD_SYSTEM_ADMINISTRATOR",
            "XROAD_SECURITYSERVER_OBSERVER"
    );

    @Test
    @DisplayName("API key created with all 5 roles is persisted with all roles present")
    void apiKeyCreatedWithAllPrivileges(SsApiTestContainerSetup stack) {
        var apiKeys = apiKeysClient(stack);

        var keyId = given("a new API key is created with all 5 roles", () ->
                apiKeys.createKey(ALL_ROLES)
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("id"));

        then("the created key carries all 5 roles", () -> {
            var roles = apiKeys.getKey(keyId)
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .<String>getList("roles");
            assertThat(roles).containsExactlyInAnyOrderElementsOf(ALL_ROLES);
        });
    }

    @Test
    @DisplayName("API key created and then revoked is no longer in the key list")
    void apiKeyCreatedAndRevoked(SsApiTestContainerSetup stack) {
        var apiKeys = apiKeysClient(stack);

        var keyId = given("a new API key is created with REGISTRATION_OFFICER role", () ->
                apiKeys.createKey(List.of("XROAD_REGISTRATION_OFFICER"))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("id"));

        then("the key is present in the list", () -> {
            var ids = apiKeys.listKeysRaw().stream()
                    .map(k -> ((Number) k.get("id")).longValue())
                    .toList();
            assertThat(ids).contains(keyId);
        });

        when("the key is revoked", () ->
                apiKeys.deleteKey(keyId)
                        .statusCode(200));

        then("the key is absent from the list", () -> {
            var ids = apiKeys.listKeysRaw().stream()
                    .map(k -> ((Number) k.get("id")).longValue())
                    .toList();
            assertThat(ids).doesNotContain(keyId);
        });
    }

    @Test
    @DisplayName("API key created with one role and then updated to a different role set reflects the new roles")
    void apiKeyCreatedAndEdited(SsApiTestContainerSetup stack) {
        var apiKeys = apiKeysClient(stack);

        var keyId = given("a new API key is created with REGISTRATION_OFFICER role", () ->
                apiKeys.createKey(List.of("XROAD_REGISTRATION_OFFICER"))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("id"));

        var updatedRoles = List.of(
                "XROAD_SECURITY_OFFICER",
                "XROAD_SERVICE_ADMINISTRATOR",
                "XROAD_SYSTEM_ADMINISTRATOR",
                "XROAD_SECURITYSERVER_OBSERVER"
        );

        when("the key is updated to a different role set (no REGISTRATION_OFFICER)", () ->
                apiKeys.updateKey(keyId, updatedRoles)
                        .statusCode(200));

        then("the key lists the new roles and REGISTRATION_OFFICER is absent", () -> {
            var roles = apiKeys.getKey(keyId)
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .<String>getList("roles");
            assertThat(roles).containsExactlyInAnyOrderElementsOf(updatedRoles);
            assertThat(roles).doesNotContain("XROAD_REGISTRATION_OFFICER");
        });
    }

    @Test
    @DisplayName("PUT /api-keys/{id} adding a role the caller does not hold is rejected with 403")
    void apiKeyEditRejectsAddingRoleCallerDoesNotHold(SsApiTestContainerSetup stack, SsBaselineSeeder seeder) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());
        var adminUsers = new AdminUsersAdminClient(seeder.newSession());

        var sysadminUsername = "ak-sysadmin-" + Long.toString(System.nanoTime(), 36);
        var sysadminPassword = "T0pSecret!789";

        given("a SYSTEM_ADMINISTRATOR-only admin user is created", () ->
                adminUsers.createUser(sysadminUsername, sysadminPassword,
                                List.of("XROAD_SYSTEM_ADMINISTRATOR"))
                        .statusCode(201));

        var sysAdminApiKeys = new ApiKeysAdminClient(uiBaseUrl, sysadminUsername, sysadminPassword);

        var keyId = and("that user creates a key holding only its own role", () ->
                sysAdminApiKeys.createKey(List.of("XROAD_SYSTEM_ADMINISTRATOR"))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("id"));

        when("the user tries to ADD XROAD_SECURITY_OFFICER (a role it does not hold) via update", () ->
                sysAdminApiKeys.updateKey(keyId, List.of("XROAD_SYSTEM_ADMINISTRATOR", "XROAD_SECURITY_OFFICER"))
                        .statusCode(403));

        then("the key is unchanged — still only XROAD_SYSTEM_ADMINISTRATOR", () -> {
            var roles = apiKeysClient(stack).getKey(keyId)
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .<String>getList("roles");
            assertThat(roles).containsExactly("XROAD_SYSTEM_ADMINISTRATOR");
            assertThat(roles).doesNotContain("XROAD_SECURITY_OFFICER");
        });

        adminUsers.deleteUser(sysadminUsername).statusCode(200);
    }

    private ApiKeysAdminClient apiKeysClient(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        return new ApiKeysAdminClient("https://%s:%d".formatted(uiMapping.host(), uiMapping.port()));
    }
}

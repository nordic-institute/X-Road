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
package org.niis.xroad.cs.test.api.apikeys;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.ApiKeysAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * API tests for the {@code /api-keys} endpoint against the real CS backend.
 *
 * <p>The endpoint sits behind a separate Spring Security filter chain that requires HTTP Basic
 * auth (PAM). The CS Docker image ships two Linux test users:
 * <ul>
 *   <li>{@code xrd} — member of {@code xroad-security-officer}, {@code xroad-registration-officer},
 *       and {@code xroad-system-administrator}. In the CS only SYSTEM_ADMINISTRATOR has
 *       {@code CREATE_API_KEY}, {@code UPDATE_API_KEY}, {@code REVOKE_API_KEY}, and
 *       {@code VIEW_API_KEYS}; the broader role set matters for the privilege-escalation check.</li>
 *   <li>{@code xrd-sys} — member of {@code xroad-system-administrator} only. Can manage API keys
 *       but cannot assign {@code XROAD_SECURITY_OFFICER} or {@code XROAD_REGISTRATION_OFFICER}
 *       to a key because it does not hold those roles.</li>
 * </ul>
 *
 * <p>Backend rejection behaviour confirmed from
 * {@code ApiKeyService#verifyUserCanUpdateApiKeyRoles}: for each role that is <em>new</em> to the
 * key (not already assigned), {@code SecurityHelper#verifyAuthority} is called and throws
 * {@link org.springframework.security.access.AccessDeniedException} when the caller lacks that
 * role, which Spring translates to HTTP 403.
 */
@SuppressWarnings("checkstyle:magicnumber")
class ApiKeysApiTest extends CsApiTest {

    /** Covers the seeded 1s api-key list cache TTL plus scheduling jitter. */
    private static final Duration KEY_LIST_CACHE_GRACE = Duration.ofSeconds(10);

    private static final String ROLE_SECURITY_OFFICER = "XROAD_SECURITY_OFFICER";
    private static final String ROLE_REGISTRATION_OFFICER = "XROAD_REGISTRATION_OFFICER";
    private static final String ROLE_SYSTEM_ADMINISTRATOR = "XROAD_SYSTEM_ADMINISTRATOR";

    @Test
    void createApiKeyHappyPath(CsBaselineSeeder seeder) {
        var client = ApiKeysAdminClient.fullPrivilegeClient(seeder.getAdminBaseUrl());

        var keyId = Step.when("API key is created with SYSTEM_ADMINISTRATOR role", () ->
                client.createKey(List.of(ROLE_SYSTEM_ADMINISTRATOR))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("id"));

        Step.then("the created key is retrievable and carries the assigned role", () -> {
            var roles = client.getKey(keyId)
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .<String>getList("roles");
            assertThat(roles).containsExactly(ROLE_SYSTEM_ADMINISTRATOR);
        });

        client.revokeKey(keyId).statusCode(200);
    }

    @Test
    void editApiKeyHappyPath(CsBaselineSeeder seeder) {
        var client = ApiKeysAdminClient.fullPrivilegeClient(seeder.getAdminBaseUrl());

        var keyId = Step.given("API key is created with REGISTRATION_OFFICER role", () ->
                client.createKey(List.of(ROLE_REGISTRATION_OFFICER))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("id"));

        Step.when("the key is updated to SECURITY_OFFICER + SYSTEM_ADMINISTRATOR", () ->
                client.updateKey(keyId, List.of(ROLE_SECURITY_OFFICER, ROLE_SYSTEM_ADMINISTRATOR))
                        .statusCode(200));

        Step.then("the key carries the new roles and REGISTRATION_OFFICER is absent", () -> {
            var roles = client.getKey(keyId)
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .<String>getList("roles");
            assertThat(roles).containsExactlyInAnyOrder(ROLE_SECURITY_OFFICER, ROLE_SYSTEM_ADMINISTRATOR);
            assertThat(roles).doesNotContain(ROLE_REGISTRATION_OFFICER);
        });

        client.revokeKey(keyId).statusCode(200);
    }

    @Test
    void revokeApiKeyHappyPath(CsBaselineSeeder seeder) {
        var client = ApiKeysAdminClient.fullPrivilegeClient(seeder.getAdminBaseUrl());

        var keyId = Step.given("API key is created with SECURITY_OFFICER role", () ->
                client.createKey(List.of(ROLE_SECURITY_OFFICER))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("id"));

        // The key list is served from a TTL cache that can hold a snapshot taken before this
        // test's write committed (eviction happens before commit), so poll past the staleness.
        Step.then("the key appears in the list before revocation", () ->
                await().atMost(KEY_LIST_CACHE_GRACE).untilAsserted(() -> {
                    var ids = client.listKeysRaw().stream()
                            .map(k -> ((Number) k.get("id")).longValue())
                            .toList();
                    assertThat(ids).contains(keyId);
                }));

        Step.when("the key is revoked", () ->
                client.revokeKey(keyId).statusCode(200));

        Step.then("the key is absent from the list after revocation", () ->
                await().atMost(KEY_LIST_CACHE_GRACE).untilAsserted(() -> {
                    var ids = client.listKeysRaw().stream()
                            .map(k -> ((Number) k.get("id")).longValue())
                            .toList();
                    assertThat(ids).doesNotContain(keyId);
                }));
    }

    @Test
    void createRejectsRoleCallerDoesNotHold(CsBaselineSeeder seeder) {
        var sysAdminOnlyClient = ApiKeysAdminClient.systemAdminOnlyClient(seeder.getAdminBaseUrl());

        Step.when("xrd-sys (SYSTEM_ADMINISTRATOR only) creates a key requesting SECURITY_OFFICER", () ->
                sysAdminOnlyClient.createKey(List.of(ROLE_SECURITY_OFFICER))
                        .statusCode(403));

        Step.and("xrd-sys creates a key requesting REGISTRATION_OFFICER", () ->
                sysAdminOnlyClient.createKey(List.of(ROLE_REGISTRATION_OFFICER))
                        .statusCode(403));

        Step.then("full-privilege xrd can create the same key — proving rejection is authorization-driven", () -> {
            var fullClient = ApiKeysAdminClient.fullPrivilegeClient(seeder.getAdminBaseUrl());
            var keyId = fullClient.createKey(List.of(ROLE_SECURITY_OFFICER))
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getLong("id");
            fullClient.revokeKey(keyId).statusCode(200);
        });
    }

    @Test
    void editRejectsAddingRoleCallerDoesNotHold(CsBaselineSeeder seeder) {
        var fullClient = ApiKeysAdminClient.fullPrivilegeClient(seeder.getAdminBaseUrl());
        var sysAdminOnlyClient = ApiKeysAdminClient.systemAdminOnlyClient(seeder.getAdminBaseUrl());

        var keyId = Step.given("full-privilege xrd creates a key with SYSTEM_ADMINISTRATOR only", () ->
                fullClient.createKey(List.of(ROLE_SYSTEM_ADMINISTRATOR))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("id"));

        Step.when("xrd-sys attempts to add SECURITY_OFFICER (a role it does not hold)", () ->
                sysAdminOnlyClient.updateKey(keyId,
                        List.of(ROLE_SYSTEM_ADMINISTRATOR, ROLE_SECURITY_OFFICER))
                        .statusCode(403));

        Step.then("the key is unchanged — still only SYSTEM_ADMINISTRATOR", () -> {
            var roles = fullClient.getKey(keyId)
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .<String>getList("roles");
            assertThat(roles).containsExactly(ROLE_SYSTEM_ADMINISTRATOR);
            assertThat(roles).doesNotContain(ROLE_SECURITY_OFFICER);
        });

        Step.and("the full-privilege xrd can add SECURITY_OFFICER to the same key — proving the rejection was authorization-driven", () ->
                fullClient.updateKey(keyId,
                        List.of(ROLE_SYSTEM_ADMINISTRATOR, ROLE_SECURITY_OFFICER))
                        .statusCode(200));

        fullClient.revokeKey(keyId).statusCode(200);
    }
}

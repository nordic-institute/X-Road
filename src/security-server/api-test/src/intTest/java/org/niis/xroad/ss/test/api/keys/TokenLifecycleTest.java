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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.TokensAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for token lifecycle operations that mutate the shared softToken-0 or the token registry.
 */
@DisplayName("Token lifecycle — PIN change and token deletion")
@ResourceLock("softToken-0")
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
class TokenLifecycleTest extends SsApiTest {

    private static final String SOFT_TOKEN = "0";
    private static final String ORIGINAL_PIN = SsBaselineSeeder.SS_TOKEN_PIN;

    @Test
    @DisplayName("Token PIN can be changed and the token is left logged in with the original PIN restored")
    void tokenPinCanBeChanged(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);
        var newPin = "T0ken1zer3New";

        given("softToken-0 is logged in", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            assertThat(token.getLoggedIn()).isTrue();
        });

        when("the PIN is changed from the original to a new PIN", () ->
                tokens.updateTokenPin(SOFT_TOKEN, ORIGINAL_PIN, newPin)
                        .statusCode(204));

        then("the token is logged out after PIN change", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            assertThat(token.getLoggedIn()).isFalse();
        });

        and("logging in with the new PIN succeeds", () ->
                tokens.loginToken(SOFT_TOKEN, newPin)
                        .statusCode(200));

        and("the token is logged in again", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            assertThat(token.getLoggedIn()).isTrue();
        });

        when("the PIN is changed back to the original", () ->
                tokens.updateTokenPin(SOFT_TOKEN, newPin, ORIGINAL_PIN)
                        .statusCode(204));

        then("the token is logged out again", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            assertThat(token.getLoggedIn()).isFalse();
        });

        and("logging in with the original PIN restores the token to a logged-in state", () -> {
            tokens.loginToken(SOFT_TOKEN, ORIGINAL_PIN).statusCode(200);
            var token = tokens.getToken(SOFT_TOKEN);
            assertThat(token.getLoggedIn()).isTrue();
        });
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    @DisplayName("An inactive HSM token inserted directly into the DB can be deleted via the API")
    void inactiveTokenCanBeDeleted(SsBaselineSeeder seeder, SsApiTestContainerSetup stack) {
        var tokenName = "hsmToken-for-deletion";
        var tokenExternalId = "del-test-" + System.nanoTime();

        given("a hardware (inactive) token is inserted into the signer_tokens table", () ->
                execSql(stack, """
                        INSERT INTO signer_tokens (external_id, type, friendly_name)
                        VALUES ('%s', 'hardwareToken', '%s')
                        """.formatted(tokenExternalId, tokenName)));

        and("the signer is restarted so it reloads the token registry from DB", () -> {
            stack.restartService(SsApiTestContainerSetup.SIGNER);
            var signerMapping = stack.getContainerMapping(SsApiTestContainerSetup.SIGNER, Port.QUARKUS_HEALTH);
            var livenessUrl = "http://%s:%d/q/health/live".formatted(signerMapping.host(), signerMapping.port());
            await()
                    .pollDelay(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofSeconds(3))
                    .atMost(Duration.ofSeconds(90))
                    .ignoreExceptions()
                    .until(() -> "UP".equals(
                            org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory
                                    .given().get(livenessUrl).jsonPath().getString("status")));
            var adminTokens = new TokensAdminClient(seeder.newSession());
            if (!Boolean.TRUE.equals(adminTokens.getToken(SOFT_TOKEN).getLoggedIn())) {
                adminTokens.loginToken(SOFT_TOKEN, ORIGINAL_PIN).statusCode(200);
            }
        });

        var tokenId = when("the token list is queried and the new token ID is found", () -> {
            var session = seeder.newSession();
            var allTokens = new TokensAdminClient(session).listTokens();
            return allTokens.stream()
                    .filter(t -> tokenName.equals(t.getName()))
                    .map(t -> t.getId())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Inserted token not found in token list"));
        });

        then("the token is not available (hardware token not present in hw)", () -> {
            var session = seeder.newSession();
            var token = new TokensAdminClient(session).getToken(tokenId);
            assertThat(token.getAvailable()).isFalse();
        });

        when("the inactive token is deleted via the API", () -> {
            var session = seeder.newSession();
            new TokensAdminClient(session).deleteToken(tokenId)
                    .statusCode(204);
        });

        then("the token is no longer in the token list", () -> {
            var session = seeder.newSession();
            var allTokens = new TokensAdminClient(session).listTokens();
            var stillPresent = allTokens.stream()
                    .anyMatch(t -> tokenId.equals(t.getId()));
            assertThat(stillPresent).isFalse();
        });
    }

    private void execSql(SsApiTestContainerSetup stack, String sql) {
        try {
            var result = stack.execInContainer(
                    SsApiTestContainerSetup.DB_SERVERCONF,
                    "psql", "-U", "postgres", "serverconf", "-c", sql);
            log.debug("psql: {}", result.getStdout());
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("psql failed: " + result.getStderr());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute SQL", e);
        }
    }
}

/*
 * The MIT License
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
package org.niis.xroad.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Case-5 self-call: the management Security Server (ss0) registers its own clients. Environment
 * provisioning already performs this — {@code TestService} and {@code TestSaved} are added to ss0
 * and registered through {@code PUT /clients/{id}/register}, a management request that travels
 * through ss0's own management client because ss0 is the management Security Server itself. This
 * class makes that outcome an explicit, repeatable assertion instead of an implicit provisioning
 * side effect.
 *
 * <p>Read-only: it only queries client status via the admin API, so it is safe to re-run against a
 * warm cluster. It asserts the externally observable outcome (registered status) only, never which
 * participant context carried the registration request, so it holds unchanged across the later
 * {@code -mgmt}-to-SYSTEM cutover.
 *
 * <p>Only k8s and LXD run the dataspace protocol stack that case-5 self-calls travel through; the
 * Compose facade does not, so this scenario self-skips there via {@link Assumptions}.
 */
@DisplayName("SS clients - management Security Server self-registration")
@Order(50)
@SuppressWarnings("checkstyle:magicnumber")
class SsClientRegistrationTest extends E2eTest {

    private static final String ADMIN_USERNAME = "xrd";
    private static final String ADMIN_PASSWORD = "secret123!";
    private static final String REGISTERED_STATUS = "REGISTERED";

    @Test
    @DisplayName("ss0's own TestService and TestSaved clients reach REGISTERED state through ss0 itself")
    void managementSecurityServerSelfRegistersItsOwnClients(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; case-5 self-registration is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        then("DEV:COM:1234:TestService is REGISTERED on ss0", () ->
                awaitClientStatus(env, "ss0", "DEV:COM:1234:TestService", REGISTERED_STATUS));
        and("DEV:COM:1234:TestSaved is REGISTERED on ss0", () ->
                awaitClientStatus(env, "ss0", "DEV:COM:1234:TestSaved", REGISTERED_STATUS));
    }

    private void awaitClientStatus(E2eEnvironment env, String envName, String clientId, String expectedStatus) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.UI, SsStackSetup.Port.UI);
        var baseUrl = "https://%s:%s".formatted(mapping.host(), mapping.port());

        var loginResponse = RestAssuredFactory.given()
                .formParam("username", ADMIN_USERNAME)
                .formParam("password", ADMIN_PASSWORD)
                .post(baseUrl + "/login");
        assertThat(loginResponse.getStatusCode()).isEqualTo(200);

        var xsrfToken = loginResponse.getCookie("XSRF-TOKEN");
        var sessionCookies = loginResponse.getCookies();

        Awaitility.await()
                .pollInterval(Duration.ofSeconds(2))
                .timeout(Duration.ofSeconds(60))
                .untilAsserted(() -> {
                    var response = RestAssuredFactory.given()
                            .cookies(sessionCookies)
                            .header("X-XSRF-TOKEN", xsrfToken)
                            .get(baseUrl + "/api/v1/clients/" + clientId);
                    assertThat(response.getStatusCode())
                            .as("GET /clients/%s on %s", clientId, envName)
                            .isEqualTo(200);
                    assertThat(response.jsonPath().getString("status"))
                            .as("status of client %s on %s", clientId, envName)
                            .isEqualTo(expectedStatus);
                });
    }
}

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

import ee.ria.xroad.common.util.MimeUtils;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.properties.config.keys.ServerConfConfigKeys;
import org.niis.xroad.e2e.AdminApi.AdminSession;

import java.time.Duration;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.e2e.AdminApi.MOCK1_CLIENT_ID;
import static org.niis.xroad.e2e.AdminApi.MOCK1_SERVICE_CODE;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.authed;
import static org.niis.xroad.e2e.AdminApi.callMock1;
import static org.niis.xroad.e2e.AdminApi.enableServiceDescription;
import static org.niis.xroad.e2e.AdminApi.findServiceDescriptionId;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Proves the operator's maintenance-window round trip against a running stack: a service description is
 * disabled with a notice, a permitted consumer's call is refused with the notice text, the description is
 * re-enabled, and the same consumer succeeds again — all without a restart or a manual cache clear.
 *
 * <p>Reuses ss0's own self-call shape ({@link SsProxyDspSelfCallTest}'s consumer/service pair,
 * {@code DEV:COM:1234:TestService} calling its own {@code mock1} REST service through ss0's proxy) rather
 * than provisioning a new member: that pair already carries a self-access grant, so no new registration or
 * access-rights ceremony is needed, and disabling a subsystem's own service does not narrow what any other
 * scenario in the suite can reach.
 *
 * <p><b>Ordering.</b> Runs after both classes sharing {@code @Order(350)} ({@link SsProxyDspRuntimeMemberTest},
 * {@link SsReverseProxyMessageFlowTest}) and before {@link SsMonitoringTest}, whose {@code mock1} and
 * {@code restapi} calls need the service description enabled. The suite executes as one serial class order,
 * so the disable/re-enable window only ever exists inside this class's own run — no concurrently-ordered
 * scenario can observe the service mid-disable. The description is left enabled on every exit path: the
 * re-enable step runs in a {@code finally} around the disabled-fault assertion, and the first step also
 * re-enables unconditionally before taking the baseline, so a prior run that died between disable and
 * re-enable does not fail this one's baseline.
 *
 * <p><b>Fault shape.</b> The provider's {@code ServerRestMessageProcessor} raises {@code SERVICE_DISABLED}
 * after the access-rights check, carrying the notice in its details, and prefixes the code with
 * {@code server.serverproxy} as it encodes the fault. The fault crosses the wire as a SOAP fault (X-Road's
 * proxy-to-proxy protocol represents faults this way even for REST calls); on the consumer side a
 * {@code server.}-prefixed code makes the client-facing handler answer 500, not 400. The REST caller
 * therefore sees an HTTP 500 with an {@code X-Road-Error} header and a JSON body whose {@code type} carries
 * the same prefixed code and whose {@code message} carries the exact notice text.
 */
@DisplayName("SS proxy - operator disables a service, a consumer sees the notice, the operator re-enables it")
@Order(375)
@SuppressWarnings("checkstyle:magicnumber")
class SsProxyServiceDisableRoundTripTest extends E2eTest {

    private static final String SS0_ENV = "ss0";

    private static final String EXPECTED_RESPONSE_MESSAGE = "Hello, world from POST service!";
    private static final String DISABLED_NOTICE = "Scheduled maintenance window, please retry once it closes";

    private static final String EXPECTED_FAULT_CODE = SERVER_SERVERPROXY_X + "." + ErrorCode.SERVICE_DISABLED.code();

    /**
     * The provider proxy only stops refusing calls (and only starts refusing them) once its own
     * {@code CachingServerConfImpl} reload picks up the disabled flag — there is no invalidation signal
     * on either transition, so both waits are bounded by that cache's own TTL,
     * {@link ServerConfConfigKeys#CACHE_PERIOD}, plus slack for the poll and the request itself.
     */
    private static final Duration SERVERCONF_CACHE_EXPIRY_TIMEOUT =
            Duration.ofSeconds(ServerConfConfigKeys.CACHE_PERIOD.convertedDefaultValue() + 30);
    private static final Duration SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL = Duration.ofSeconds(3);

    @Test
    @DisplayName("Disabling a service description returns the operator's notice to a permitted consumer; "
            + "re-enabling it restores calls, with no restart")
    void disablingAndReEnablingTakeEffectWithoutRestart(E2eEnvironment env) {
        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));

        var serviceDescriptionId = and("the service description backing TestService's mock1 REST service is found", () ->
                findServiceDescriptionId(ss0BaseUrl, ss0Session, MOCK1_CLIENT_ID, MOCK1_SERVICE_CODE));

        and("the service description starts enabled, tolerating a prior run left it disabled", () ->
                enableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId));

        then("a permitted consumer calls the service and succeeds, establishing the baseline", () ->
                awaitCallSucceeds(env));

        and("the operator disables the service description, supplying a notice", () ->
                disableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId));

        try {
            then("the same consumer's next call is refused with a SERVICE_DISABLED fault carrying the notice", () ->
                    awaitCallFailsWithDisabledNotice(env));
        } finally {
            and("the operator re-enables the service description", () ->
                    enableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId));
        }

        then("the same consumer calls again and succeeds, closing the maintenance window", () ->
                awaitCallSucceeds(env));
    }

    private void disableServiceDescription(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId) {
        var body = """
                {"disabled_notice": "%s"}
                """.formatted(DISABLED_NOTICE);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .put(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId + "/disable");
        assertThat(response.getStatusCode()).as("disable service description %s", serviceDescriptionId).isBetween(200, 299);
    }

    private void awaitCallSucceeds(E2eEnvironment env) {
        Awaitility.await()
                .pollDelay(Duration.ZERO)
                .pollInterval(SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL)
                .timeout(SERVERCONF_CACHE_EXPIRY_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> callMock1(env, SS0_ENV)
                        .statusCode(200)
                        .body("message", equalTo(EXPECTED_RESPONSE_MESSAGE)));
    }

    private void awaitCallFailsWithDisabledNotice(E2eEnvironment env) {
        Awaitility.await()
                .pollDelay(Duration.ZERO)
                .pollInterval(SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL)
                .timeout(SERVERCONF_CACHE_EXPIRY_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> callMock1(env, SS0_ENV)
                        .statusCode(500)
                        .header(MimeUtils.HEADER_ERROR, equalTo(EXPECTED_FAULT_CODE))
                        .body("type", equalTo(EXPECTED_FAULT_CODE))
                        .body("message", containsString(DISABLED_NOTICE)));
    }

}

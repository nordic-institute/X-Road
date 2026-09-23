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

import java.time.Duration;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.e2e.AdminApi.MOCK1_CLIENT_ID;
import static org.niis.xroad.e2e.AdminApi.MOCK1_SERVICE_ID;
import static org.niis.xroad.e2e.AdminApi.addRestServiceDescription;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.callService;
import static org.niis.xroad.e2e.AdminApi.deleteServiceDescription;
import static org.niis.xroad.e2e.AdminApi.disableServiceDescription;
import static org.niis.xroad.e2e.AdminApi.discoverBackendUrl;
import static org.niis.xroad.e2e.AdminApi.enableServiceDescription;
import static org.niis.xroad.e2e.AdminApi.grantConsumerAccessRights;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Proves the path issue 03's round trip cannot: a consumer who has never held an agreement for the
 * asset negotiates fresh — catalog fetch, offer, agreement, transfer — against a service that is
 * disabled before its first ever call, and still receives the operator's notice.
 *
 * <p>Freshness is guaranteed by construction rather than asserted: the service code added here did
 * not exist before this test ran, so no consumer anywhere can hold a prior agreement for it, and the
 * service is disabled before any call is ever made against it. This is deliberately not the same
 * service {@link SsProxyServiceDisableRoundTripTest} disables — that scenario's consumer already
 * holds an agreement from the suite's baseline traffic, so its disabled call rides the cached-agreement
 * path instead.
 *
 * <p><b>Provider and backend.</b> The new service is added to ss0's own {@code TestService}
 * subsystem — the same client {@code mock1} belongs to — addressing {@code mock1}'s backend one path
 * segment up from its own service description's URL. A REST service description's URL must be unique
 * within its client ({@code ServiceDescriptionService.checkDuplicateUrl}), so this scenario cannot reuse
 * {@code mock1}'s exact URL without colliding with {@code mock1}'s own, already-enabled description; the
 * one path segment stripped off the base is instead supplied as the consumer's own trailing REST path,
 * which the provider proxy appends back onto the backend address
 * ({@code DefaultRestServiceHandlerImpl.concatPath}), landing on the exact same, real WireMock stub
 * {@code mock1} itself calls — so the re-enabled call has a genuine 200 to assert on, not a stubbed-out
 * shortcut.
 *
 * <p><b>Consumer.</b> {@code DEV:COM:4321:TestClient} on ss1 — cross-server, mirroring the manual proof
 * (issue 03 comments, "correction" entry) — rather than ss0's own {@code TestService}: {@code TestService}
 * already negotiated with itself and with {@code mock1} elsewhere in this suite, so a self-call would not
 * demonstrate a consumer new to this asset the way a distinct member does. Confirmed to ride the
 * dataspace protocol connector like every other REST/SOAP exchange in this suite, on all four CI variants,
 * per XRDADR-40 Option A (the data plane is the permanent baseline, not a per-environment opt-in) and
 * {@link DsControlPlaneDbOps}'s own class doc ("the Compose facade runs the full dataspace protocol stack
 * too, but does not wire up this database-ops glue") — the DB-assertion gate other DSP scenarios apply is
 * about database introspection capability, not about which environments route traffic through the
 * connector, so this scenario runs unconditionally rather than self-skipping.
 *
 * <p><b>Ordering.</b> Runs after {@link SsProxyServiceDisableRoundTripTest} (@Order 375), before
 * {@link SsMonitoringTest} (@Order 400), whose {@code mock1} and {@code restapi} calls need those service
 * descriptions left enabled — this scenario's own service description is removed entirely in a
 * {@code finally}, so it leaves nothing behind for {@link SsMonitoringTest} to trip over.
 *
 * <p><b>Fault shape.</b> Same as {@link SsProxyServiceDisableRoundTripTest}: the provider's
 * {@code ServerRestMessageProcessor} raises {@code SERVICE_DISABLED} after the access-rights check,
 * which crosses the wire as a SOAP fault and surfaces to the REST caller as an HTTP 500 with an
 * {@code X-Road-Error} header and a JSON body carrying the prefixed code and the exact notice text.
 */
@DisplayName("SS proxy - a fresh negotiation for a disabled service delivers the operator's notice")
@Order(390)
@SuppressWarnings("checkstyle:magicnumber")
class SsProxyFreshNegotiationDisabledNoticeTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String SS1_ENV = "ss1";

    private static final String CONSUMER_CLIENT_ID = "DEV:COM:4321:TestClient";
    private static final String CONSUMER_X_ROAD_CLIENT = "DEV/COM/4321/TestClient";

    /** A service code no consumer could ever hold a prior agreement for: it does not exist before this test adds it. */
    private static final String NEW_SERVICE_CODE = "freshMock1";

    private static final String EXPECTED_RESPONSE_MESSAGE = "Hello, world from POST service!";
    private static final String DISABLED_NOTICE = "Scheduled maintenance window, please retry once it closes";

    private static final String EXPECTED_FAULT_CODE = SERVER_SERVERPROXY_X + "." + ErrorCode.SERVICE_DISABLED.code();

    /** Same cache-period-bounded wait {@link SsProxyServiceDisableRoundTripTest} uses for the re-enable/success step. */
    private static final Duration SERVERCONF_CACHE_EXPIRY_TIMEOUT =
            Duration.ofSeconds(ServerConfConfigKeys.CACHE_PERIOD.convertedDefaultValue() + 30);
    private static final Duration SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL = Duration.ofSeconds(3);

    /**
     * The first call against the freshly added service also needs the provider's catalog enumeration
     * cache (default TTL 60 s) to have picked up the new asset, on top of the serverconf cache the
     * disabled fault itself is bounded by — the wider window {@link SsProxyDspRuntimeMemberTest} uses for
     * the same reason.
     */
    private static final Duration CATALOG_VISIBILITY_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration CATALOG_VISIBILITY_POLL_INTERVAL = Duration.ofSeconds(10);

    @Test
    @DisplayName("A consumer with no prior agreement negotiates fresh against a service disabled before its "
            + "first call, and is refused with the notice; re-enabling restores the call")
    void freshNegotiationForADisabledServiceDeliversTheNotice(E2eEnvironment env) {
        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));

        var backendUrl = and("mock1's backend URL is discovered", () ->
                discoverBackendUrl(ss0BaseUrl, ss0Session, MOCK1_SERVICE_ID));
        var lastSegment = backendUrl.lastIndexOf('/');
        var backendBaseUrl = backendUrl.substring(0, lastSegment);
        var servicePath = "/r1/DEV/COM/1234/TestService/" + NEW_SERVICE_CODE + backendUrl.substring(lastSegment);

        var serviceDescriptionId = and(
                "a new REST service description, under a service code that never existed before this test, is "
                        + "added to TestService, addressing mock1's backend one path segment up", () ->
                        addRestServiceDescription(ss0BaseUrl, ss0Session, MOCK1_CLIENT_ID, backendBaseUrl, NEW_SERVICE_CODE));

        try {
            and("the new service description is enabled", () ->
                    enableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId));

            and("the cross-server consumer is granted access to the new service", () ->
                    grantConsumerAccessRights(ss0BaseUrl, ss0Session, MOCK1_CLIENT_ID, CONSUMER_CLIENT_ID, NEW_SERVICE_CODE));

            and("the operator disables the new service description with a notice, before any call has ever reached it", () ->
                    disableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId, DISABLED_NOTICE));

            then("the consumer's first ever call negotiates fresh and is refused with a SERVICE_DISABLED "
                    + "fault carrying the notice", () ->
                    awaitFreshCallFailsWithDisabledNotice(env, servicePath));

            and("the operator re-enables the service description", () ->
                    enableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId));

            then("the same consumer calls again and succeeds", () ->
                    awaitCallSucceeds(env, servicePath));
        } finally {
            and("the service description is removed, leaving no trace of the fresh service code", () ->
                    deleteServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId));
        }
    }

    private void awaitFreshCallFailsWithDisabledNotice(E2eEnvironment env, String servicePath) {
        Awaitility.await()
                .pollDelay(Duration.ZERO)
                .pollInterval(CATALOG_VISIBILITY_POLL_INTERVAL)
                .timeout(CATALOG_VISIBILITY_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> callService(env, SS1_ENV, CONSUMER_X_ROAD_CLIENT, servicePath)
                        .statusCode(500)
                        .header(MimeUtils.HEADER_ERROR, equalTo(EXPECTED_FAULT_CODE))
                        .body("type", equalTo(EXPECTED_FAULT_CODE))
                        .body("message", containsString(DISABLED_NOTICE)));
    }

    private void awaitCallSucceeds(E2eEnvironment env, String servicePath) {
        Awaitility.await()
                .pollDelay(Duration.ZERO)
                .pollInterval(SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL)
                .timeout(SERVERCONF_CACHE_EXPIRY_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> callService(env, SS1_ENV, CONSUMER_X_ROAD_CLIENT, servicePath)
                        .statusCode(200)
                        .body("message", equalTo(EXPECTED_RESPONSE_MESSAGE)));
    }

}

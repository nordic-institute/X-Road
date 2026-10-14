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

import io.restassured.response.ValidatableResponse;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.e2e.AdminApi.addGlobalGroup;
import static org.niis.xroad.e2e.AdminApi.addGlobalGroupMembers;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.deleteGlobalGroup;
import static org.niis.xroad.e2e.AdminApi.grantConsumerAccessRights;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.revokeConsumerAccessRights;
import static org.niis.xroad.e2e.AgreementRecords.CONSUMER;
import static org.niis.xroad.e2e.AgreementRecords.PROVIDER;
import static org.niis.xroad.e2e.AgreementRecords.TRANSFER_COMPLETED;
import static org.niis.xroad.e2e.AgreementRecords.TRANSFER_STARTED;
import static org.niis.xroad.e2e.DisabledServiceFault.EXPECTED_RESPONSE_MESSAGE;
import static org.niis.xroad.e2e.DisabledServiceFault.SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL;
import static org.niis.xroad.e2e.DisabledServiceFault.SERVERCONF_CACHE_EXPIRY_TIMEOUT;
import static org.niis.xroad.e2e.Mock1Fixture.MOCK1_CLIENT_ID;
import static org.niis.xroad.e2e.Mock1Fixture.MOCK1_SERVICE_ID;
import static org.niis.xroad.e2e.Mock1Fixture.callService;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * How the provider-minted agreement token and its fallbacks look from outside, observed only through what the
 * provider serves or denies, through the negotiation and transfer records of both control planes and through the
 * data-flow records in both proxies' serverconf.
 *
 * <p>Every scenario adds its own REST service under a service code unique to the run ({@link FreshRestService}),
 * so no agreement from earlier traffic can answer for it, and removes the service again in a {@code finally}.
 * The provider publishes one offer per access-control subject and mints a token only for an agreement whose
 * subject is a subsystem that the live access-control list still grants, so each scenario grants access through
 * the admin API first; a consumer with no entry gets no offer and is refused before any negotiation.
 *
 * <ul>
 *   <li>Refresh: two requests separated by more than the token lifetime are both served, the second, a single
 *       call, under exactly one new transfer of the same agreement and with no second negotiation. The transfer
 *       the refresh superseded then completes on both control planes and in both proxies' serverconf
 *       {@code dataflow_state}, the newest keeps running, and the agreement stays registered.</li>
 *   <li>Two subsystems of one member each negotiate their own agreement for the same service, each agreement
 *       naming its own subsystem.</li>
 *   <li>An agreement whose subject is a global group is decided by the access-control list, which serves the
 *       request while the group entry exists and denies it once the entry is revoked. The group lists the
 *       consumer's member as well as its subsystem, because the data space policy compares the member while the
 *       list compares the subsystem.</li>
 * </ul>
 *
 * <p>Only k8s and LXD run the dataspace protocol stack and implement {@link DsControlPlaneDbOps} and
 * {@link ServerConfDbOps}; the Compose facade does not, so these scenarios self-skip there via
 * {@link Assumptions}.
 *
 * <p>Runs after {@link SsProxyServiceDisableRoundTripTest} and before {@link SsProxyFreshNegotiationDisabledNoticeTest}
 * and {@link SsMonitoringTest}.
 */
@DisplayName("SS proxy - agreement token path, refresh and group-scoped agreements")
@Order(385)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class SsProxyAgreementTokenTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String SS1_ENV = "ss1";
    private static final String CS_ENV = "aux";

    private static final String TEST_CLIENT_ID = "DEV:COM:4321:TestClient";
    private static final String TEST_CLIENT_X_ROAD_ID = "DEV/COM/4321/TestClient";
    private static final String TEST_CLIENT_MEMBER_ID = "DEV:COM:4321";
    private static final String TEST_CLIENT_SERVICE_ID = TEST_CLIENT_ID + ":mock1";

    private static final String TEST_SERVICE_X_ROAD_ID = "DEV/COM/1234/TestService";
    private static final String TEST_SAVED_ID = "DEV:COM:1234:TestSaved";
    private static final String TEST_SAVED_X_ROAD_ID = "DEV/COM/1234/TestSaved";
    private static final String OWNER_MEMBER_CTX_ID = "DEV:COM:1234";

    private static final String ACCESS_DENIED_FAULT = SERVER_SERVERPROXY_X + "." + ErrorCode.ACCESS_DENIED.code();

    /** Long enough for the provider's catalog enumeration cache to expire and publish a service created in this run. */
    private static final Duration CATALOG_VISIBILITY_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration CATALOG_VISIBILITY_POLL_INTERVAL = Duration.ofSeconds(5);

    /** Long enough for the central server's group to reach the provider through its global configuration. */
    private static final Duration GLOBAL_CONF_PROPAGATION_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration GLOBAL_CONF_PROPAGATION_POLL_INTERVAL = Duration.ofSeconds(5);

    /** Past a token's expiry by more than the verifier's clock-skew leeway and the consumer's cache margin. */
    private static final Duration PAST_TOKEN_LIFETIME = ProxyConfigKeys.AGREEMENT_TOKEN_TOKEN_TTL.convertedDefaultValue()
            .plus(ProxyConfigKeys.AGREEMENT_TOKEN_EXPIRY_LEEWAY.convertedDefaultValue())
            .plus(ProxyConfigKeys.DSP_CACHE_MARGIN.convertedDefaultValue())
            .plusSeconds(5);

    /** The expired-transfer sweep runs every 30 s and waits a 30 s grace after a token's expiry. */
    private static final Duration SUPERSEDED_TRANSFER_COMPLETION_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration RECORD_POLL_INTERVAL = Duration.ofSeconds(3);
    private static final Duration FLOW_STATE_TIMEOUT = Duration.ofSeconds(30);

    @Test
    @Order(1)
    @DisplayName("A request after the token lifetime is served under the same agreement with no new negotiation, "
            + "and the superseded transfer completes on both control planes and in both proxies")
    void refreshReusesTheAgreementWithoutRenegotiating(E2eEnvironment env) {
        Assumptions.assumeTrue(isWired(env), () -> notWiredMessage(env));
        var consumerRecords = new AgreementRecords((DsControlPlaneDbOps) env, SS1_ENV);
        var providerRecords = new AgreementRecords((DsControlPlaneDbOps) env, SS0_ENV);
        var consumerFlows = new DataFlowRecords((ServerConfDbOps) env, SS1_ENV);
        var providerFlows = new DataFlowRecords((ServerConfDbOps) env, SS0_ENV);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());
        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));

        var service = given("a service unique to this run is added to TestService on ss0", () ->
                FreshRestService.create(ss0BaseUrl, ss0Session, MOCK1_CLIENT_ID, MOCK1_SERVICE_ID, "tokenRefresh"));
        try {
            and("the cross-server consumer is granted access to it", () ->
                    grantConsumerAccessRights(ss0BaseUrl, ss0Session, MOCK1_CLIENT_ID, TEST_CLIENT_ID, service.serviceCode()));

            when("the consumer calls the service for the first time and is served", () ->
                    awaitServed(env, SS1_ENV, TEST_CLIENT_X_ROAD_ID, service.servicePath()));

            var wireAgreementId = then("exactly one negotiation was recorded on the consumer control plane", () -> {
                var agreementIds = consumerRecords.finalizedAgreementIds(CONSUMER, TEST_CLIENT_MEMBER_ID, service.assetId());
                assertThat(agreementIds).hasSize(1);
                assertThat(consumerRecords.negotiationCount(CONSUMER, TEST_CLIENT_MEMBER_ID, service.assetId())).isOne();
                return agreementIds.getFirst();
            });

            var transfersBeforeRefresh = and("the consumer's transfers under the agreement are noted", () ->
                    transferIds(consumerRecords, CONSUMER, wireAgreementId));

            and("the token lifetime passes", () -> Awaitility.await()
                    .pollDelay(PAST_TOKEN_LIFETIME)
                    .timeout(PAST_TOKEN_LIFETIME.plusSeconds(10))
                    .until(() -> true));

            when("the consumer calls the service again, once, and is served", () ->
                    callService(env, SS1_ENV, TEST_CLIENT_X_ROAD_ID, service.servicePath())
                            .statusCode(200)
                            .body("message", equalTo(EXPECTED_RESPONSE_MESSAGE)));

            then("no further negotiation was recorded, and the second request ran under exactly one new transfer "
                    + "of the same agreement", () -> {
                        assertThat(consumerRecords.finalizedAgreementIds(CONSUMER, TEST_CLIENT_MEMBER_ID, service.assetId()))
                                .containsExactly(wireAgreementId);
                        assertThat(consumerRecords.negotiationCount(CONSUMER, TEST_CLIENT_MEMBER_ID, service.assetId()))
                                .isOne();
                        var transfersAfterRefresh = transferIds(consumerRecords, CONSUMER, wireAgreementId);
                        assertThat(transfersAfterRefresh).startsWith(transfersBeforeRefresh.toArray(String[]::new));
                        assertThat(transfersAfterRefresh).hasSize(transfersBeforeRefresh.size() + 1);
                    });

            and("the superseded transfer completes on both control planes while the newest keeps running", () -> {
                awaitSupersededTransfersCompleted(consumerRecords, CONSUMER, wireAgreementId);
                awaitSupersededTransfersCompleted(providerRecords, PROVIDER, wireAgreementId);
            });

            and("the superseded transfer's flow is COMPLETED in the serverconf of both proxies", () -> {
                assertSupersededFlowsCompleted(consumerFlows, consumerRecords, CONSUMER, wireAgreementId);
                assertSupersededFlowsCompleted(providerFlows, providerRecords, PROVIDER, wireAgreementId);
            });

            and("the agreement is still registered on both control planes", () -> {
                assertThat(consumerRecords.agreementRegistered(wireAgreementId)).isTrue();
                assertThat(providerRecords.agreementRegistered(wireAgreementId)).isTrue();
            });
        } finally {
            and("the service is removed", () -> service.remove(ss0BaseUrl, ss0Session));
        }
    }

    @Test
    @Order(2)
    @DisplayName("Two subsystems of one member calling the same service each negotiate their own agreement")
    void subsystemsOfOneMemberEachNegotiateTheirOwnAgreement(E2eEnvironment env) {
        Assumptions.assumeTrue(isWired(env), () -> notWiredMessage(env));
        var consumerRecords = new AgreementRecords((DsControlPlaneDbOps) env, SS0_ENV);
        var providerRecords = new AgreementRecords((DsControlPlaneDbOps) env, SS1_ENV);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());
        var ss1BaseUrl = adminBaseUrl(env, SS1_ENV);
        var ss1Session = given("an admin session is established on ss1", () -> login(ss1BaseUrl));

        var service = given("a service unique to this run is added to TestClient on ss1", () ->
                FreshRestService.create(ss1BaseUrl, ss1Session, TEST_CLIENT_ID, TEST_CLIENT_SERVICE_ID, "tokenSubsystems"));
        try {
            and("two subsystems of member DEV:COM:1234 are each granted access to it by their own entry", () -> {
                grantConsumerAccessRights(ss1BaseUrl, ss1Session, TEST_CLIENT_ID, Mock1Fixture.MOCK1_CLIENT_ID,
                        service.serviceCode());
                grantConsumerAccessRights(ss1BaseUrl, ss1Session, TEST_CLIENT_ID, TEST_SAVED_ID, service.serviceCode());
            });

            when("the first subsystem calls the service and is served", () ->
                    awaitServed(env, SS0_ENV, TEST_SERVICE_X_ROAD_ID, service.servicePath()));
            and("the second subsystem calls the service and is served", () ->
                    awaitServed(env, SS0_ENV, TEST_SAVED_X_ROAD_ID, service.servicePath()));

            then("the consumer control plane holds two distinct agreements, one per subsystem", () -> {
                var agreementIds = consumerRecords.finalizedAgreementIds(CONSUMER, OWNER_MEMBER_CTX_ID, service.assetId());
                assertThat(agreementIds).hasSize(2).doesNotHaveDuplicates();
                assertThat(consumerRecords.negotiationCount(CONSUMER, OWNER_MEMBER_CTX_ID, service.assetId())).isEqualTo(2);
            });

            and("each agreement names the subsystem it was negotiated for", () -> {
                var forFirst = consumerRecords.finalizedAgreementIdsNaming(CONSUMER, OWNER_MEMBER_CTX_ID, service.assetId(),
                        MOCK1_CLIENT_ID);
                var forSecond = consumerRecords.finalizedAgreementIdsNaming(CONSUMER, OWNER_MEMBER_CTX_ID, service.assetId(),
                        TEST_SAVED_ID);
                assertThat(forFirst).as("agreements naming %s", MOCK1_CLIENT_ID).hasSize(1);
                assertThat(forSecond).as("agreements naming %s", TEST_SAVED_ID).hasSize(1);
                assertThat(forFirst).doesNotContainAnyElementsOf(forSecond);
            });

            and("the provider control plane holds the same two agreements", () -> {
                var consumerSide = consumerRecords.finalizedAgreementIds(CONSUMER, OWNER_MEMBER_CTX_ID, service.assetId());
                assertThat(providerRecords.finalizedAgreementIds(PROVIDER, TEST_CLIENT_MEMBER_ID, service.assetId()))
                        .containsExactlyInAnyOrderElementsOf(consumerSide);
            });
        } finally {
            and("the service is removed", () -> service.remove(ss1BaseUrl, ss1Session));
        }
    }

    @Test
    @Order(3)
    @DisplayName("An agreement under a global-group entry is served through the access-control list and denied "
            + "once the group entry is revoked")
    void globalGroupAgreementIsDecidedByTheAccessControlList(E2eEnvironment env) {
        Assumptions.assumeTrue(isWired(env), () -> notWiredMessage(env));
        var consumerRecords = new AgreementRecords((DsControlPlaneDbOps) env, SS1_ENV);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());
        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var csBaseUrl = adminBaseUrl(env, CS_ENV);
        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));
        var csSession = given("an admin session is established on the Central Server", () -> login(csBaseUrl));

        var groupCode = "e2e-token-" + System.currentTimeMillis();
        var groupId = "DEV:" + groupCode;
        var service = given("a service unique to this run is added to TestService on ss0", () ->
                FreshRestService.create(ss0BaseUrl, ss0Session, MOCK1_CLIENT_ID, MOCK1_SERVICE_ID, "tokenGroup"));
        try {
            and("a global group holding the consumer's member and subsystem exists", () -> {
                addGlobalGroup(csBaseUrl, csSession, groupCode);
                addGlobalGroupMembers(csBaseUrl, csSession, groupCode, List.of(TEST_CLIENT_MEMBER_ID, TEST_CLIENT_ID));
            });

            and("the provider grants the service to the group alone, once the group has reached its global configuration", () ->
                    Awaitility.await()
                            .pollInterval(GLOBAL_CONF_PROPAGATION_POLL_INTERVAL)
                            .timeout(GLOBAL_CONF_PROPAGATION_TIMEOUT)
                            .untilAsserted(() -> grantConsumerAccessRights(
                                    ss0BaseUrl, ss0Session, MOCK1_CLIENT_ID, groupId, service.serviceCode())));

            when("the consumer calls the service and is served", () ->
                    awaitServed(env, SS1_ENV, TEST_CLIENT_X_ROAD_ID, service.servicePath()));

            then("an agreement was negotiated for the consumer", () ->
                    assertThat(consumerRecords.finalizedAgreementIds(CONSUMER, TEST_CLIENT_MEMBER_ID, service.assetId()))
                            .hasSize(1));

            and("the provider revokes the group entry", () ->
                    revokeConsumerAccessRights(ss0BaseUrl, ss0Session, MOCK1_CLIENT_ID, groupId, service.serviceCode()));

            then("the consumer's next call is denied by the access-control list", () ->
                    awaitDenied(env, SS1_ENV, TEST_CLIENT_X_ROAD_ID, service.servicePath()));
        } finally {
            and("the service is removed", () -> service.remove(ss0BaseUrl, ss0Session));
            and("the global group is removed", () -> deleteGlobalGroup(csBaseUrl, csSession, groupCode));
        }
    }

    private static boolean isWired(E2eEnvironment env) {
        return env instanceof DsControlPlaneDbOps && env instanceof ServerConfDbOps;
    }

    private static String notWiredMessage(E2eEnvironment env) {
        return ("%s does not run the dataspace protocol stack with database access; "
                + "the agreement token scenarios are only wired for k8s and LXD").formatted(env.getClass().getSimpleName());
    }

    private static List<String> transferIds(AgreementRecords records, String side, String wireAgreementId) {
        return records.transfers(side, wireAgreementId).stream().map(AgreementRecords.Transfer::id).toList();
    }

    private void awaitServed(E2eEnvironment env, String envName, String xRoadClient, String servicePath) {
        DisabledServiceFault.awaitCallSucceeds(() -> callService(env, envName, xRoadClient, servicePath),
                CATALOG_VISIBILITY_TIMEOUT, CATALOG_VISIBILITY_POLL_INTERVAL);
    }

    private void awaitDenied(E2eEnvironment env, String envName, String xRoadClient, String servicePath) {
        awaitDenied(() -> callService(env, envName, xRoadClient, servicePath));
    }

    /**
     * Polls until the provider's access-control list refuses the call: it stops serving only once its cache of the
     * revoked entry expires.
     */
    private void awaitDenied(Supplier<ValidatableResponse> call) {
        Awaitility.await()
                .pollDelay(Duration.ZERO)
                .pollInterval(SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL)
                .timeout(SERVERCONF_CACHE_EXPIRY_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> call.get()
                        .statusCode(500)
                        .header(HEADER_ERROR, equalTo(ACCESS_DENIED_FAULT))
                        .body("type", equalTo(ACCESS_DENIED_FAULT)));
    }

    /**
     * Polls until every transfer but the newest under the agreement is COMPLETED and the newest is STARTED.
     */
    private void awaitSupersededTransfersCompleted(AgreementRecords records, String side, String wireAgreementId) {
        Awaitility.await()
                .pollInterval(RECORD_POLL_INTERVAL)
                .timeout(SUPERSEDED_TRANSFER_COMPLETION_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var transfers = records.transfers(side, wireAgreementId);
                    assertThat(transfers).as("%s transfers of agreement %s", side, wireAgreementId).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(transfers.getLast().state()).as("newest %s transfer", side).isEqualTo(TRANSFER_STARTED);
                    assertThat(transfers.subList(0, transfers.size() - 1)).as("superseded %s transfers", side)
                            .allMatch(transfer -> transfer.state() == TRANSFER_COMPLETED);
                });
    }

    /**
     * Asserts that the proxy recorded every transfer but the newest as a COMPLETED flow, the flow being keyed by
     * the transfer's id on the control plane that signals this proxy.
     */
    private void assertSupersededFlowsCompleted(DataFlowRecords flows, AgreementRecords records, String side,
                                                String wireAgreementId) {
        var transfers = records.transfers(side, wireAgreementId);
        var supersededIds = transfers.subList(0, transfers.size() - 1).stream().map(AgreementRecords.Transfer::id).toList();
        assertThat(supersededIds).as("superseded %s transfers of agreement %s", side, wireAgreementId).isNotEmpty();
        Awaitility.await()
                .pollInterval(RECORD_POLL_INTERVAL)
                .timeout(FLOW_STATE_TIMEOUT)
                .untilAsserted(() -> supersededIds.forEach(flowId -> assertThat(flows.state(flowId))
                        .as("%s flow %s in serverconf dataflow_state", side, flowId)
                        .contains(DataFlowRecords.COMPLETED)));
    }
}

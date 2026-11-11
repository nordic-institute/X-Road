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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.addLocalClient;
import static org.niis.xroad.e2e.AdminApi.addRestServiceDescription;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.awaitClientsRegistered;
import static org.niis.xroad.e2e.AdminApi.awaitMemberCredentialIssued;
import static org.niis.xroad.e2e.AdminApi.discoverBackendUrl;
import static org.niis.xroad.e2e.AdminApi.enableServiceDescription;
import static org.niis.xroad.e2e.AdminApi.grantConsumerAccessRights;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.registerClient;
import static org.niis.xroad.e2e.DisabledServiceFault.awaitCallSucceeds;
import static org.niis.xroad.e2e.Mock1Fixture.MOCK1_CLIENT_ID;
import static org.niis.xroad.e2e.Mock1Fixture.MOCK1_SERVICE_CODE;
import static org.niis.xroad.e2e.Mock1Fixture.MOCK1_SERVICE_ID;
import static org.niis.xroad.e2e.Mock1Fixture.MOCK1_X_ROAD_CLIENT;
import static org.niis.xroad.e2e.Mock1Fixture.callService;
import static org.niis.xroad.e2e.SignCertificateOps.provisionSignCertificate;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Proves that credentials issued after the issuer's active status list is full still verify. A list has
 * at least 131,072 slots, so the scenario cannot fill one by issuing: it sets the active list's
 * {@code currentIndex} to its {@code bitstringSize} directly in the issuer's database, the state the
 * issuer reaches once the last slot is taken.
 *
 * <p>Two new members are then onboarded to ss0 at run time, each getting a membership credential. Each
 * serves a REST service to ss0's established {@code TestService} consumer, so the credential is verified
 * by the counter-party on the data space path, through ss0's proxy. Afterwards the issuer's database must
 * hold exactly one more status list than before, and that list must hold a slot for every credential
 * issued here: the credentials shared one new list instead of each opening its own.
 *
 * <p>The scenario is single-shot. It changes the issuer's data permanently and creates members that stay,
 * so it runs only while the issuer has exactly one status list and that list has room, as on a freshly
 * provisioned environment, and self-skips otherwise.
 *
 * <p>Needs {@link CsIssuerDbOps}, which only the LXD adapter implements, and the dataspace protocol
 * stack, so it self-skips elsewhere via {@link Assumptions}, like {@link SsMemberDeprovisioningTest}.
 */
@DisplayName("SS dataspace - credentials issued after the first status list is full still verify")
@Order(380)
class SsStatusListRolloverTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String CA_ENV = "ca";

    private static final String X_ROAD_INSTANCE = "DEV";
    private static final String MEMBER_CLASS = "COM";

    private static final Duration REGISTRATION_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration FIRST_LIST_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration FIRST_LIST_TIMEOUT = Duration.ofMinutes(2);
    /** The provider control plane serves its catalog from a cache with a bounded TTL; calls retry across it. */
    private static final Duration CATALOG_VISIBILITY_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration CATALOG_VISIBILITY_POLL_INTERVAL = Duration.ofSeconds(10);

    private static final String STATUS_LIST_FILTER =
            "participant_context_id = 'issuer' AND usage = 'StatusList'";
    private static final String ACTIVE_STATUS_LIST_FILTER = STATUS_LIST_FILTER + " AND metadata->>'isActive' = 'true'";
    private static final String NOT_FULL_FILTER =
            "(metadata->>'currentIndex')::int < (metadata->>'bitstringSize')::int";

    private static final List<Member> MEMBERS = List.of(
            new Member("6001", "RolloverService1"),
            new Member("6002", "RolloverService2"));

    private record Member(String code, String subsystem) {
        String ctxId() {
            return X_ROAD_INSTANCE + ":" + MEMBER_CLASS + ":" + code;
        }

        String clientId() {
            return ctxId() + ":" + subsystem;
        }

        String servicePath() {
            return "/r1/" + X_ROAD_INSTANCE + "/" + MEMBER_CLASS + "/" + code + "/" + subsystem + "/" + MOCK1_SERVICE_CODE;
        }
    }

    private record StatusListCounts(int total, int withRoom) {
    }

    @Test
    @DisplayName("Two credentials issued after the active status list is marked full verify through ss0's proxy")
    void credentialsIssuedAfterTheFirstListIsFullStillVerify(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof CsIssuerDbOps && env instanceof DsControlPlaneDbOps,
                () -> "%s does not expose the Central Server's issuer database and the dataspace protocol stack"
                        .formatted(env.getClass().getSimpleName()));
        var issuerDb = (CsIssuerDbOps) env;
        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());
        given("the issuer has created its first active status list", () -> awaitActiveStatusList(issuerDb));
        var before = statusListCounts(issuerDb);
        Assumptions.assumeTrue(before.total() == 1 && before.withRoom() == 1, "needs a freshly provisioned environment");
        when("the active status list is marked full", () -> markActiveStatusListFull(issuerDb));
        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var csBaseUrl = CentralServerApi.baseUrl(env);
        var caBaseUrl = caBaseUrl(env);
        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));
        and("two new members are added to ss0 with CA-signed sign certificates and submitted for registration", () -> {
            for (var member : MEMBERS) {
                addLocalClient(ss0BaseUrl, ss0Session, member.clientId());
                provisionSignCertificate(ss0BaseUrl, ss0Session, caBaseUrl, env.securityServerAddress(SS0_ENV),
                        member.ctxId(), member.code());
                registerClient(ss0BaseUrl, ss0Session, member.clientId());
            }
        });
        var csSession = given("an admin session is established on the Central Server", () -> login(csBaseUrl));
        then("the Central Server approves both requests and ss0 reports both clients as REGISTERED", () ->
                awaitClientsRegistered(ss0BaseUrl, ss0Session, csBaseUrl, csSession,
                        MEMBERS.stream().map(Member::clientId).toList(), REGISTRATION_TIMEOUT));
        and("each new member serves a REST service to the established consumer", () -> {
            var backendUrl = discoverBackendUrl(ss0BaseUrl, ss0Session, MOCK1_SERVICE_ID);
            for (var member : MEMBERS) {
                var serviceDescriptionId = addRestServiceDescription(
                        ss0BaseUrl, ss0Session, member.clientId(), backendUrl, MOCK1_SERVICE_CODE);
                enableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId);
                grantConsumerAccessRights(ss0BaseUrl, ss0Session, member.clientId(), MOCK1_CLIENT_ID, MOCK1_SERVICE_CODE);
            }
        });
        then("both members' membership credentials are issued", () -> {
            for (var member : MEMBERS) {
                awaitMemberCredentialIssued(ss0BaseUrl, ss0Session, member.ctxId());
            }
        });
        then("the established consumer reaches both members through ss0's proxy, verifying their credentials", () -> {
            for (var member : MEMBERS) {
                awaitCallSucceeds(() -> callService(env, SS0_ENV, MOCK1_X_ROAD_CLIENT, member.servicePath()),
                        CATALOG_VISIBILITY_TIMEOUT, CATALOG_VISIBILITY_POLL_INTERVAL);
            }
        });
        then("the issuer holds exactly one new status list, with a slot taken for every credential issued here", () -> {
            assertThat(statusListCounts(issuerDb).total())
                    .as("status lists after issuing past a full list, before: %d", before.total())
                    .isEqualTo(before.total() + 1);
            assertThat(newestStatusListIndex(issuerDb))
                    .as("slots taken in the new status list")
                    .isGreaterThanOrEqualTo(MEMBERS.size());
        });
    }

    private int querySingleInt(CsIssuerDbOps issuerDb, String sql) {
        return Integer.parseInt(issuerDb.execCsIssuerSql(CentralServerApi.CS_ENV, sql).trim());
    }

    private StatusListCounts statusListCounts(CsIssuerDbOps issuerDb) {
        var sql = "SELECT COUNT(*), COUNT(*) FILTER (WHERE %s) FROM credential_resource WHERE %s"
                .formatted(NOT_FULL_FILTER, STATUS_LIST_FILTER);
        var counts = issuerDb.execCsIssuerSql(CentralServerApi.CS_ENV, sql).split("\\|", -1);
        return new StatusListCounts(Integer.parseInt(counts[0].trim()), Integer.parseInt(counts[1].trim()));
    }

    /** The slots taken in the status list that still has room, which is the one opened after the full list. */
    private int newestStatusListIndex(CsIssuerDbOps issuerDb) {
        return querySingleInt(issuerDb, "SELECT COALESCE(MAX((metadata->>'currentIndex')::int), -1) "
                + "FROM credential_resource WHERE " + STATUS_LIST_FILTER + " AND " + NOT_FULL_FILTER);
    }

    /** Waits for the issuer's first active status list, which exists once it has issued any credential. */
    private void awaitActiveStatusList(CsIssuerDbOps issuerDb) {
        var sql = "SELECT COUNT(*) FROM credential_resource WHERE " + ACTIVE_STATUS_LIST_FILTER;
        Awaitility.await()
                .alias("the issuer's first active status list")
                .pollInterval(FIRST_LIST_POLL_INTERVAL)
                .timeout(FIRST_LIST_TIMEOUT)
                .ignoreExceptions()
                .until(() -> querySingleInt(issuerDb, sql) > 0);
    }

    /**
     * The credential store's {@code metadata} column is {@code JSON}, which has no update operators, so
     * the value is cast to {@code JSONB} for {@code jsonb_set} and back. A full list stays active, so the
     * filter also requires room.
     */
    private void markActiveStatusListFull(CsIssuerDbOps issuerDb) {
        var sql = """
                WITH updated AS (
                  UPDATE credential_resource
                  SET metadata = jsonb_set(metadata::jsonb, '{currentIndex}', metadata::jsonb -> 'bitstringSize')::json
                  WHERE %s AND %s
                  RETURNING 1)
                SELECT COUNT(*) FROM updated
                """.formatted(ACTIVE_STATUS_LIST_FILTER, NOT_FULL_FILTER);
        assertThat(querySingleInt(issuerDb, sql)).as("active status list rows marked full").isEqualTo(1);
    }

    private String caBaseUrl(E2eEnvironment env) {
        var mapping = env.getContainerMapping(CA_ENV, SsStackSetup.CA, SsStackSetup.Port.CA_API);
        return "http://%s:%s".formatted(mapping.host(), mapping.port());
    }
}

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
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.AdminApi.AdminSession;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.e2e.AdminApi.addLocalClient;
import static org.niis.xroad.e2e.AdminApi.addRestServiceDescription;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.authed;
import static org.niis.xroad.e2e.AdminApi.awaitClientsRegistered;
import static org.niis.xroad.e2e.AdminApi.discoverBackendUrl;
import static org.niis.xroad.e2e.AdminApi.enableServiceDescription;
import static org.niis.xroad.e2e.AdminApi.grantConsumerAccessRights;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.registerClient;
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
 * hold more status lists than before: the credentials were assigned to a new list.
 *
 * <p>Needs {@link CsIssuerDbOps}, which only the LXD adapter implements, and the dataspace protocol
 * stack, so it self-skips elsewhere via {@link Assumptions}, like {@link SsMemberDeprovisioningTest}.
 *
 * <p>Runs after {@link SsMemberDeprovisioningTest} and {@link SsProxyServiceDisableRoundTripTest}
 * (both {@code @Order(375)}), before {@link SsProxyFreshNegotiationDisabledNoticeTest}.
 */
@DisplayName("SS dataspace - credentials issued after the first status list is full still verify")
@Order(380)
@SuppressWarnings({"checkstyle:magicnumber", "unchecked"})
class SsStatusListRolloverTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String CA_ENV = "ca";

    private static final String X_ROAD_INSTANCE = "DEV";
    private static final String MEMBER_CLASS = "COM";
    private static final String REST_SERVICE_CODE = "mock1";

    private static final String CONSUMER_CLIENT_ID = "DEV:COM:1234:TestService";
    private static final String CONSUMER_X_ROAD_ID = "DEV/COM/1234/TestService";
    private static final String EXISTING_SERVICE_ID = Mock1Fixture.MOCK1_SERVICE_ID;

    private static final String SIGN_KEY_TOKEN_ID = "0";
    private static final String SIGN_KEY_SERIAL_NUMBER = "DEV/SS0/COM";

    private static final String ISSUED_CREDENTIAL_STATUS = "ISSUED";
    private static final String EXPECTED_RESPONSE_MESSAGE = "Hello, world from POST service!";

    private static final Duration PROVISIONING_POLL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration PROVISIONING_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration FIRST_LIST_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration CATALOG_VISIBILITY_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration CATALOG_VISIBILITY_POLL_INTERVAL = Duration.ofSeconds(10);

    private static final String STATUS_LIST_FILTER =
            "participant_context_id = 'issuer' AND usage = 'StatusList'";
    private static final String ACTIVE_STATUS_LIST_FILTER = STATUS_LIST_FILTER + " AND metadata->>'isActive' = 'true'";

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
            return "/r1/" + X_ROAD_INSTANCE + "/" + MEMBER_CLASS + "/" + code + "/" + subsystem + "/" + REST_SERVICE_CODE;
        }
    }

    @Test
    @DisplayName("Two credentials issued after the active status list is marked full verify through ss0's proxy")
    void credentialsIssuedAfterTheFirstListIsFullStillVerify(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof CsIssuerDbOps && env instanceof DsControlPlaneDbOps,
                () -> "%s does not expose the Central Server's issuer database and the dataspace protocol stack"
                        .formatted(env.getClass().getSimpleName()));
        var issuerDb = (CsIssuerDbOps) env;

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var listsBefore = given("the issuer has created its first active status list", () ->
                awaitActiveStatusList(issuerDb));

        when("the active status list is marked full", () -> markActiveStatusListFull(issuerDb));

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var csBaseUrl = CentralServerApi.baseUrl(env);
        var caBaseUrl = caBaseUrl(env);
        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));

        and("two new members are added to ss0 with CA-signed sign certificates and submitted for registration", () -> {
            for (var member : MEMBERS) {
                addLocalClient(ss0BaseUrl, ss0Session, member.clientId());
                provisionSignCertificate(env, ss0BaseUrl, ss0Session, caBaseUrl, member);
                registerClient(ss0BaseUrl, ss0Session, member.clientId());
            }
        });

        var csSession = given("an admin session is established on the Central Server", () -> login(csBaseUrl));

        then("the Central Server approves both requests and ss0 reports both clients as REGISTERED", () ->
                awaitClientsRegistered(ss0BaseUrl, ss0Session, csBaseUrl, csSession,
                        MEMBERS.stream().map(Member::clientId).toList(), Duration.ofMinutes(3)));

        and("each new member serves a REST service to the established consumer", () -> {
            var backendUrl = discoverBackendUrl(ss0BaseUrl, ss0Session, EXISTING_SERVICE_ID);
            for (var member : MEMBERS) {
                var serviceDescriptionId = addRestServiceDescription(
                        ss0BaseUrl, ss0Session, member.clientId(), backendUrl, REST_SERVICE_CODE);
                enableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId);
                grantConsumerAccessRights(ss0BaseUrl, ss0Session, member.clientId(), CONSUMER_CLIENT_ID, REST_SERVICE_CODE);
            }
        });

        then("both members' membership credentials are issued", () -> {
            for (var member : MEMBERS) {
                awaitMemberCredentialIssued(ss0BaseUrl, ss0Session, member);
            }
        });

        then("the established consumer reaches both members through ss0's proxy, verifying their credentials", () -> {
            for (var member : MEMBERS) {
                awaitCallSucceeds(env, member)
                        .body("message", equalTo(EXPECTED_RESPONSE_MESSAGE));
            }
        });

        then("the issuer holds a second status list", () ->
                assertThat(countStatusLists(issuerDb))
                        .as("status lists after issuing past a full list, before: %d", listsBefore)
                        .isGreaterThan(listsBefore));
    }

    private int countStatusLists(CsIssuerDbOps issuerDb) {
        return Integer.parseInt(issuerDb.execCsIssuerSql(CentralServerApi.CS_ENV,
                "SELECT COUNT(*) FROM credential_resource WHERE " + STATUS_LIST_FILTER).trim());
    }

    /** Waits for the issuer's first active status list, which exists once it has issued any credential. */
    private int awaitActiveStatusList(CsIssuerDbOps issuerDb) {
        var sql = "SELECT COUNT(*) FROM credential_resource WHERE " + ACTIVE_STATUS_LIST_FILTER;
        try {
            Awaitility.await()
                    .pollInterval(PROVISIONING_POLL_INTERVAL)
                    .timeout(FIRST_LIST_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> Integer.parseInt(issuerDb.execCsIssuerSql(CentralServerApi.CS_ENV, sql).trim()) > 0);
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException("Timed out waiting for the issuer's first active status list", e);
        }
        return countStatusLists(issuerDb);
    }

    /**
     * The credential store's {@code metadata} column is {@code JSON}, which has no update operators, so
     * the value is cast to {@code JSONB} for {@code jsonb_set} and back.
     */
    private void markActiveStatusListFull(CsIssuerDbOps issuerDb) {
        var sql = """
                WITH updated AS (
                  UPDATE credential_resource
                  SET metadata = jsonb_set(metadata::jsonb, '{currentIndex}', metadata::jsonb -> 'bitstringSize')::json
                  WHERE %s
                  RETURNING 1)
                SELECT COUNT(*) FROM updated
                """.formatted(ACTIVE_STATUS_LIST_FILTER);
        var updated = Integer.parseInt(issuerDb.execCsIssuerSql(CentralServerApi.CS_ENV, sql).trim());
        assertThat(updated).as("active status list rows marked full").isEqualTo(1);
    }

    private String caBaseUrl(E2eEnvironment env) {
        var mapping = env.getContainerMapping(CA_ENV, SsStackSetup.CA, SsStackSetup.Port.CA_API);
        return "http://%s:%s".formatted(mapping.host(), mapping.port());
    }

    /**
     * Provisions a CA-signed sign certificate for the member on ss0's token: SIGNING CSR, test CA signing,
     * import, and an explicit activation when the import left the certificate inactive. A certificate the
     * token already holds for the member, from an earlier run, is reused.
     */
    private void provisionSignCertificate(E2eEnvironment env, String ss0BaseUrl, AdminSession ss0, String caBaseUrl,
                                          Member member) {
        var existing = findSignCertificate(ss0BaseUrl, ss0, member);
        if (existing != null && Boolean.TRUE.equals(existing.get("active"))) {
            return;
        }
        var certHash = existing != null
                ? (String) ((Map<String, Object>) existing.get("certificate_details")).get("hash")
                : importSignCertificate(env, ss0BaseUrl, ss0, caBaseUrl, member);

        var response = authed(ss0).put(ss0BaseUrl + "/api/v1/token-certificates/" + certHash + "/activate");
        assertThat(response.getStatusCode())
                .as("activate the sign certificate %s of %s (204, or 409 if the import already activated it)",
                        certHash, member.ctxId())
                .isIn(204, 409);
    }

    private String importSignCertificate(E2eEnvironment env, String ss0BaseUrl, AdminSession ss0, String caBaseUrl,
                                         Member member) {
        var csr = generateSignCsr(env, ss0BaseUrl, ss0, member);
        var csrResponse = authed(ss0).get(ss0BaseUrl + "/api/v1/keys/" + csr.keyId() + "/csrs/" + csr.csrId() + "?csr_format=PEM");
        assertThat(csrResponse.getStatusCode()).as("fetch the CSR PEM of %s", member.ctxId()).isEqualTo(200);

        var signed = RestAssuredFactory.given()
                .multiPart("certreq", "sign.csr.pem", csrResponse.getBody().asByteArray(), "application/octet-stream")
                .multiPart("type", "sign")
                .post(caBaseUrl + "/testca/sign");
        assertThat(signed.getStatusCode()).as("sign the CSR of %s with the test CA", member.ctxId()).isEqualTo(200);

        var imported = authed(ss0)
                .multiPart("certificate", "sign_key_cert.pem", signed.getBody().asByteArray(), "application/octet-stream")
                .post(ss0BaseUrl + "/api/v1/token-certificates");
        assertThat(imported.getStatusCode()).as("import the sign certificate of %s", member.ctxId()).isEqualTo(201);
        return imported.jsonPath().getString("certificate_details.hash");
    }

    private record GeneratedCsr(String keyId, String csrId) {
    }

    private GeneratedCsr generateSignCsr(E2eEnvironment env, String ss0BaseUrl, AdminSession ss0, Member member) {
        var caResponse = authed(ss0).get(ss0BaseUrl + "/api/v1/certificate-authorities");
        assertThat(caResponse.getStatusCode()).as("look up ss0's approved certificate authorities").isEqualTo(200);
        var body = """
                {
                  "key_label": "Sign key %s",
                  "csr_generate_request": {
                    "key_usage_type": "SIGNING",
                    "ca_name": "%s",
                    "csr_format": "DER",
                    "member_id": "%s",
                    "subject_field_values": {
                      "CN": "%s",
                      "C": "FI",
                      "O": "Test client",
                      "subjectAltName": "%s",
                      "serialNumber": "%s"
                    }
                  }
                }
                """.formatted(member.code(), caResponse.jsonPath().getString("[0].name"), member.ctxId(), member.code(),
                env.securityServerAddress(SS0_ENV), SIGN_KEY_SERIAL_NUMBER);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/tokens/" + SIGN_KEY_TOKEN_ID + "/keys-with-csrs");
        assertThat(response.getStatusCode()).as("generate a SIGNING CSR for %s", member.ctxId()).isEqualTo(200);
        return new GeneratedCsr(response.jsonPath().getString("key.id"), response.jsonPath().getString("csr_id"));
    }

    private Map<String, Object> findSignCertificate(String ss0BaseUrl, AdminSession ss0, Member member) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/tokens/" + SIGN_KEY_TOKEN_ID);
        assertThat(response.getStatusCode()).as("look up ss0's token %s", SIGN_KEY_TOKEN_ID).isEqualTo(200);
        for (Map<String, Object> key : response.jsonPath().getList("keys", Map.class)) {
            for (Map<String, Object> cert : (List<Map<String, Object>>) key.get("certificates")) {
                if (member.ctxId().equals(cert.get("owner_id"))) {
                    return cert;
                }
            }
        }
        return null;
    }

    private void awaitMemberCredentialIssued(String ss0BaseUrl, AdminSession ss0, Member member) {
        try {
            Awaitility.await()
                    .pollInterval(PROVISIONING_POLL_INTERVAL)
                    .timeout(PROVISIONING_POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/dataspace/provisioning-status");
                        return response.getStatusCode() == 200
                                && ISSUED_CREDENTIAL_STATUS.equals(response.jsonPath().getString(
                                "participant_contexts.find { it.kind == 'MEMBER' && it.participant_id == '"
                                        + member.ctxId() + "' }.credential_status"));
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    "Timed out waiting for participant context '%s' to reach an ISSUED membership credential"
                            .formatted(member.ctxId()), e);
        }
    }

    /**
     * The provider control plane serves its catalog from a cache with a bounded TTL, so a service added
     * just now becomes visible to the consumer only after the cache expires. The call is retried across
     * that window.
     */
    private ValidatableResponse awaitCallSucceeds(E2eEnvironment env, Member member) {
        var last = new AtomicReference<ValidatableResponse>();
        Awaitility.await()
                .pollInterval(CATALOG_VISIBILITY_POLL_INTERVAL)
                .timeout(CATALOG_VISIBILITY_TIMEOUT)
                .untilAsserted(() -> {
                    var mapping = env.getContainerMapping(SS0_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
                    var response = RestAssuredFactory.given()
                            .body(Mock1Fixture.MOCK1_REQUEST_BODY)
                            .header("Content-Type", "application/json")
                            .header("x-road-client", CONSUMER_X_ROAD_ID)
                            .post("http://%s:%s%s".formatted(mapping.host(), mapping.port(), member.servicePath()))
                            .then();
                    last.set(response);
                    assertThat(response.extract().statusCode())
                            .as("consumer call to %s via ss0 proxy", member.ctxId())
                            .isEqualTo(200);
                });
        return last.get();
    }
}

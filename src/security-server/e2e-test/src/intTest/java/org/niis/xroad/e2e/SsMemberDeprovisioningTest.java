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

import ee.ria.xroad.common.identifier.ClientId;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.addLocalClient;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.awaitClientRegistered;
import static org.niis.xroad.e2e.AdminApi.awaitMemberCredentialIssued;
import static org.niis.xroad.e2e.AdminApi.deleteClient;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.registerClient;
import static org.niis.xroad.e2e.AdminApi.unregisterClient;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Proves the feature's end-to-end teardown guarantee against real dataspace services: deleting a
 * member's last client should make its dataspace presence disappear — the DID document stops resolving
 * at the identity hub and the participant context is gone from the control plane. A second, disabled
 * scenario covers the Central Server revoking the member's membership credential at the collocated
 * issuer, without touching anyone else's, once it does so.
 *
 * <p>Registers a dedicated member ({@code DEV:COM:5678:DeprovisioningService}), new to this run,
 * entirely through ss0's own admin API — local client add, sign-key CSR generation, signing by the test
 * CA, import, registration — via {@link MemberSignCertificate}, shared with
 * {@link SsProxyDspRuntimeMemberTest} — so this scenario never touches a member any earlier class in the
 * suite depends on.
 * Once the member's participant context and membership credential are confirmed provisioned (so the
 * later "gone" assertions prove an actual removal, not a state that was never reached), its only client
 * is unregistered and deleted. Deleting a member's last client is, once binding rows are written at
 * provisioning time, what flips the member's participant binding to decommissioned in the same
 * transaction as the delete — the trigger the background reconciler then converges on. The scenario
 * polls the identity hub and the control-plane database directly, the way a counter-party or an operator
 * would observe the removal, rather than the internal {@code /dataspace/provisioning-status} admin
 * endpoint — that endpoint only reports live contexts and simply drops a member once teardown converges,
 * so it cannot distinguish "torn down" from "never provisioned".
 *
 * <p>The credential-revocation scenario is disabled because the Central Server revokes no credentials yet.
 * Once enabled it runs after the teardown scenario, observes the issuer's credential store through
 * {@link CsIssuerDbOps}, which today only the LXD adapter implements, and self-skips on any other
 * environment via {@link Assumptions}.
 *
 * <p>Only k8s and LXD run the dataspace protocol stack; the Compose facade does not implement
 * {@link DsControlPlaneDbOps}, so this scenario self-skips there via {@link Assumptions}, exactly like
 * {@link SsProxyDspRuntimeMemberTest}.
 *
 * <p>Runs after {@link SsProxyDspRuntimeMemberTest}, before {@link SsMonitoringTest} (whose
 * operational-data assertions accumulate over the whole run and should not observe a member appearing
 * and disappearing).
 */
@DisplayName("SS dataspace - deleting a member's last client tears down its dataspace presence")
@Order(375)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class SsMemberDeprovisioningTest extends E2eTest {

    private static final String SS0_ENV = "ss0";

    private static final String X_ROAD_INSTANCE = "DEV";
    private static final String MEMBER_CLASS = "COM";
    private static final String MEMBER_CODE = "5678";
    private static final String SUBSYSTEM_CODE = "DeprovisioningService";

    /** The member's (only) client id, deterministic from the constants above. */
    private static final String CLIENT_ID =
            X_ROAD_INSTANCE + ":" + MEMBER_CLASS + ":" + MEMBER_CODE + ":" + SUBSYSTEM_CODE;

    /** The member's {@code INSTANCE:CLASS:CODE} id, as the admin API reports it. */
    private static final String MEMBER_ID = X_ROAD_INSTANCE + ":" + MEMBER_CLASS + ":" + MEMBER_CODE;

    /** The ctx-id {@code ParticipantIdentifierScheme.memberCtxId} derives for this member. */
    private static final String MEMBER_CTX_ID = MEMBER_ID;

    private static final MemberSignCertificate SIGN_CERTIFICATE =
            new MemberSignCertificate(SS0_ENV, MEMBER_ID, MEMBER_CODE, "Sign key 5678");

    /**
     * ss0's own owner member, provisioned once at environment bring-up and left untouched by this
     * scenario's deletions. Its credential must survive the revocation this scenario expects — both
     * because it belongs to a different member entirely, and because it is ss0's current owner, whose
     * DID row the removal rule never deletes.
     */
    private static final String OWNER_MEMBER_CLASS = "COM";
    private static final String OWNER_MEMBER_CODE = "1234";

    /** {@code VcStatus.ISSUED.code()} (EDC 0.18) — the {@code credential_resource.vc_state} of a valid, unrevoked credential. */
    private static final int VC_STATE_ISSUED = 500;
    /** {@code VcStatus.REVOKED.code()} (EDC 0.18) — the {@code credential_resource.vc_state} after issuer-side revocation. */
    private static final int VC_STATE_REVOKED = 600;

    /**
     * The reconciler that drives teardown convergence (the provisioning worker's scheduled tick) runs
     * every 30 seconds. Six ticks is a sensible margin for control-plane delete, identity-hub delete and
     * binding-row delete to converge, including a retry of a tick that hit a transient failure.
     */
    private static final Duration TEARDOWN_POLL_TIMEOUT = Duration.ofSeconds(6 * 30);
    private static final Duration TEARDOWN_POLL_INTERVAL = Duration.ofSeconds(10);

    /**
     * The identity hub's DID-serving web context port ({@code web.http.did.port} in its
     * {@code application.yaml}), distinct from its admin/gRPC ports.
     */
    private static final int IDENTITY_HUB_DID_PORT = 7183;

    /**
     * The did:web resolution path for {@link #MEMBER_CTX_ID}'s DID, following the well-known did:web
     * convention (authority, then the DID's remaining colon-segments joined by {@code /}, then
     * {@code did.json}) — the same convention {@code setup.hurl} uses to poll the issuer's own DID.
     */
    private static final String DID_DOCUMENT_PATH = "/v1/" + X_ROAD_INSTANCE + "/" + MEMBER_CLASS + "/" + MEMBER_CODE + "/did.json";

    /**
     * The identity hub's {@code did:web} endpoint answers {@code 404 Not Found} for a DID that is no
     * longer published: its controller raises an object-not-found error for an unknown DID.
     */
    private static final int DID_NOT_FOUND_STATUS = 404;

    @Test
    @Order(1)
    @DisplayName("Deleting a member's last client removes its DID document and control-plane context")
    void deletingLastClientTearsDownTheMembersDataspacePresence(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; deprovisioning convergence is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));
        var dbOps = (DsControlPlaneDbOps) env;

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var csBaseUrl = CentralServerApi.baseUrl(env);

        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));

        var clientId = when("a dedicated member's only client is added to ss0 as a local client", () ->
                addLocalClient(ss0BaseUrl, ss0Session, CLIENT_ID));

        and("a CA-signed sign certificate for the member is provisioned on ss0's token", () ->
                SIGN_CERTIFICATE.provision(env, ss0Session));

        and("its registration is submitted from ss0 to the Central Server", () ->
                registerClient(ss0BaseUrl, ss0Session, clientId));

        var csSession = given("an admin session is established on the Central Server", () -> login(csBaseUrl));

        then("the Central Server approves the pending request and ss0 reports the client as REGISTERED", () ->
                awaitClientRegistered(ss0BaseUrl, ss0Session, csBaseUrl, csSession, clientId));

        then("the member's participant context and membership credential are provisioned, so the later "
                + "absence assertions prove a real removal", () ->
                awaitMemberCredentialIssued(ss0BaseUrl, ss0Session, MEMBER_CTX_ID));

        when("the member's only client on ss0 is unregistered", () ->
                unregisterClient(ss0BaseUrl, ss0Session, clientId));

        and("the unregistered client — the member's last on ss0 — is deleted", () ->
                deleteClient(ss0BaseUrl, ss0Session, clientId));

        then("the identity hub stops serving the member's DID document within a few reconcile ticks", () ->
                awaitDidDocumentNotFound(env));

        and("the control-plane participant context for the member is gone", () ->
                awaitControlPlaneContextAbsent(dbOps));
    }

    @Test
    @Order(2)
    @Disabled("The Central Server revokes no credentials yet; enable once revocation is implemented")
    @DisplayName("Deleting a member's last client revokes its membership credential at the issuer")
    void deletingLastClientRevokesTheMembersCredential(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof CsIssuerDbOps,
                () -> ("%s does not expose the Central Server's issuer database; credential-revocation "
                        + "assertions only run where the environment implements CsIssuerDbOps")
                        .formatted(env.getClass().getSimpleName()));
        var issuerDbOps = (CsIssuerDbOps) env;

        then("the issuer revokes every credential it holds for the member on ss0", () ->
                awaitCredentialRevoked(issuerDbOps, expectedHolderDid(env, MEMBER_CLASS, MEMBER_CODE)));

        and("ss0's owner member keeps its own credential issued, unaffected by the removal", () ->
                assertCredentialRemainsIssued(issuerDbOps, expectedHolderDid(env, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE)));
    }

    /**
     * Polls the identity hub's DID-serving web context directly, the same way a counter-party resolving
     * the member's DID would, until it reports not-found for the member's DID document.
     */
    private void awaitDidDocumentNotFound(E2eEnvironment env) {
        var mapping = env.getContainerMapping(SS0_ENV, SsStackSetup.DS_IDENTITY_HUB, IDENTITY_HUB_DID_PORT);
        var didDocumentUrl = "https://%s:%s%s".formatted(mapping.host(), mapping.port(), DID_DOCUMENT_PATH);

        try {
            Awaitility.await()
                    .pollInterval(TEARDOWN_POLL_INTERVAL)
                    .timeout(TEARDOWN_POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> RestAssuredFactory.given().get(didDocumentUrl).getStatusCode() == DID_NOT_FOUND_STATUS);
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    "Timed out waiting for the identity hub to stop serving %s's DID document at %s"
                            .formatted(MEMBER_CTX_ID, didDocumentUrl), e);
        }
    }

    /**
     * Polls the control plane's own {@code participant_context} table directly for the row's absence —
     * the same table the upstream EDC participant-context store owns, unqualified because
     * {@link DsControlPlaneDbOps} already scopes the session's search path to the control-plane schema.
     */
    private void awaitControlPlaneContextAbsent(DsControlPlaneDbOps dbOps) {
        var sql = "SELECT COUNT(*) FROM participant_context WHERE participant_context_id = '" + MEMBER_CTX_ID + "'";

        try {
            Awaitility.await()
                    .pollInterval(TEARDOWN_POLL_INTERVAL)
                    .timeout(TEARDOWN_POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> Integer.parseInt(dbOps.execDsControlPlaneSql(SS0_ENV, sql).trim()) == 0);
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    "Timed out waiting for the control-plane participant context %s to be deleted".formatted(MEMBER_CTX_ID), e);
        }
    }

    /**
     * The holder DID the issuer records for a member's credential on ss0: the member identifier plus ss0's
     * registered address extended with the identity hub's DID-serving port.
     */
    private String expectedHolderDid(E2eEnvironment env, String memberClass, String memberCode) {
        var member = ClientId.Conf.create(X_ROAD_INSTANCE, memberClass, memberCode);
        var ssHost = env.securityServerAddress(SS0_ENV) + ":" + IDENTITY_HUB_DID_PORT;
        return ParticipantIdentifierScheme.memberDid(member, ssHost).toString();
    }

    /**
     * Polls the issuer's own {@code credential_resource} table directly for the holder's credential(s)
     * reaching {@code VcStatus.REVOKED}, the same way a counter-party checking the issuer's revocation
     * status would. Waits for at least one row so a holder the issuer never provisioned in the first
     * place — which would vacuously show zero non-revoked rows — cannot be mistaken for a revoked one.
     */
    private void awaitCredentialRevoked(CsIssuerDbOps dbOps, String holderDid) {
        var sql = ("SELECT COUNT(*), COUNT(*) FILTER (WHERE vc_state <> %d) "
                + "FROM credential_resource WHERE holder_id = '%s'")
                .formatted(VC_STATE_REVOKED, holderDid);

        try {
            Awaitility.await()
                    .pollInterval(TEARDOWN_POLL_INTERVAL)
                    .timeout(TEARDOWN_POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var counts = dbOps.execCsIssuerSql(CentralServerApi.CS_ENV, sql).split("\\|", -1);
                        return Integer.parseInt(counts[0].trim()) > 0 && Integer.parseInt(counts[1].trim()) == 0;
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    "Timed out waiting for the issuer to revoke every credential for holder '%s'".formatted(holderDid), e);
        }
    }

    /**
     * Confirms an unrelated holder's credential is still {@code VcStatus.ISSUED}, proving the revocation
     * this scenario triggered stayed scoped to {@link #MEMBER_CTX_ID} rather than sweeping up every
     * credential the issuer holds. A one-shot check, not a poll: nothing in this scenario's flow ever
     * touches this holder, so there is no expected transition to wait out.
     */
    private void assertCredentialRemainsIssued(CsIssuerDbOps dbOps, String holderDid) {
        var sql = ("SELECT COUNT(*) FROM credential_resource WHERE holder_id = '%s' AND vc_state = %d")
                .formatted(holderDid, VC_STATE_ISSUED);
        var issuedCount = Integer.parseInt(dbOps.execCsIssuerSql(CentralServerApi.CS_ENV, sql).trim());
        assertThat(issuedCount)
                .as("holder '%s' keeps at least one ISSUED credential, unaffected by the other member's removal", holderDid)
                .isPositive();
    }
}

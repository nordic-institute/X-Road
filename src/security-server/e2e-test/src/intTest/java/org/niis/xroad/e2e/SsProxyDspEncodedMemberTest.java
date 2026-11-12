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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.AdminApi.AdminSession;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.addLocalClient;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.awaitClientRegistered;
import static org.niis.xroad.e2e.AdminApi.awaitMemberContext;
import static org.niis.xroad.e2e.AdminApi.grantConsumerAccessRights;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.registerClient;
import static org.niis.xroad.e2e.DisabledServiceFault.awaitCallSucceeds;
import static org.niis.xroad.e2e.SignCertificateOps.provisionSignCertificate;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Disabled until did:web resolves a DID with a percent-encoded segment.
 *
 * <p>A member whose code the context-id scheme percent-encodes acts as the <b>consumer</b> of a negotiation
 * with ss1. Only the consumer's callback address is stored and used (the provider reads it from the DSP
 * message), so a provider-side encoded member would pass without the callback path encoding and prove
 * nothing. Member {@code DEV:COM:222(A)} has the context id {@code DEV:COM:222%28A%29}, so its callback
 * segment travels as {@code 222%2528A%2529}.
 *
 * <p>Parentheses are used because they are DN-safe in the sign certificate CSR, unlike {@code +}.
 *
 * <p>Once enabled, it self-skips outside k8s and LXD, which are the only environments supporting runtime
 * member registration.
 */
@DisplayName("SS proxy - disabled until did:web resolves encoded DIDs: consumer member with a percent-encoded "
        + "context id negotiates with ss1")
@Order(360)
class SsProxyDspEncodedMemberTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String SS1_ENV = "ss1";

    private static final String MEMBER_ID = "DEV:COM:222(A)";
    private static final String CONSUMER_CLIENT_ID = MEMBER_ID + ":RuntimeConsumer";
    private static final String CONSUMER_X_ROAD_CLIENT = "DEV/COM/222(A)/RuntimeConsumer";
    private static final String MEMBER_CTX_ID = "DEV:COM:222%28A%29";
    private static final String CONSUMER_DID_SUFFIX = ":v1:" + MEMBER_CTX_ID;
    private static final String CONSUMER_CALLBACK_SEGMENT = "/DEV:COM:222%2528A%2529/";

    private static final String PROVIDER_CLIENT_ID = "DEV:COM:4321:TestClient";
    private static final String PROVIDER_CTX_ID = "DEV:COM:4321";
    private static final String PROVIDER_SERVICE_CODE = "mock1";
    private static final String PROVIDER_ASSET_ID = PROVIDER_CLIENT_ID + ":" + PROVIDER_SERVICE_CODE;
    private static final String PROVIDER_SERVICE_PATH = "/r1/DEV/COM/4321/TestClient/mock1";
    private static final String PROVIDER_COUNTERPARTY_ID_LIKE = "%:v1:DEV:COM:222%";

    private static final String CREDENTIAL_STATUS_ISSUED = "ISSUED";
    private static final String CREDENTIAL_STATUS_ERROR = "ERROR";
    private static final String PUBLICATION_STATE_PUBLISHED = "PUBLISHED";
    private static final String PUBLICATION_STATE_REJECTED = "REJECTED";

    private static final Duration GRANT_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration GRANT_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration CALL_POLL_INTERVAL = Duration.ofSeconds(10);

    @Test
    @Disabled("did:web cannot resolve a DID with a percent-encoded segment yet; enable once it can")
    @DisplayName("Disabled until did:web resolves encoded DIDs: a runtime-registered consumer member with a "
            + "percent-encoded context id completes a negotiation with ss1")
    void encodedConsumerMemberCompletesNegotiation(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; runtime member provisioning is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));
        var ss1Assertions = new DspNegotiationDbAssertions((DsControlPlaneDbOps) env,
                SS1_ENV, PROVIDER_ASSET_ID, MEMBER_CTX_ID, PROVIDER_CTX_ID);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());
        var negotiationsBefore = given("the provider negotiations that exist before this run are noted", () ->
                ss1Assertions.latestProviderNegotiationCreatedAt(PROVIDER_COUNTERPARTY_ID_LIKE));

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var ss1BaseUrl = adminBaseUrl(env, SS1_ENV);
        var csBaseUrl = CentralServerApi.baseUrl(env);

        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));
        var ss1Session = given("an admin session is established on ss1", () -> login(ss1BaseUrl));

        var clientId = when("the RuntimeConsumer subsystem of member DEV:COM:222(A) is added to ss0 as a local client", () ->
                addLocalClient(ss0BaseUrl, ss0Session, CONSUMER_CLIENT_ID));

        and("a CA-signed sign certificate for the member is provisioned on ss0's token", () ->
                provisionSignCertificate(env, ss0Session, MEMBER_ID));

        and("its registration is submitted from ss0 to the Central Server", () ->
                registerClient(ss0BaseUrl, ss0Session, clientId));

        var csSession = given("an admin session is established on the Central Server", () -> login(csBaseUrl));

        then("the Central Server approves the pending request and ss0 reports the client as REGISTERED", () ->
                awaitClientRegistered(ss0BaseUrl, ss0Session, csBaseUrl, csSession, clientId));

        and("ss0 shows the member context's credential issued and its member DID published", () ->
                awaitMemberCredentialIssuedAndDidPublished(ss0BaseUrl, ss0Session));

        and("ss1 grants the new subsystem access to TestClient's mock1 service", () ->
                awaitGrant(ss1BaseUrl, ss1Session));

        when("a REST request from the new subsystem to TestClient's mock1 succeeds via the ss0 proxy "
                        + "with the expected POST service message",
                () -> awaitCallSucceeds(
                        () -> Mock1Fixture.callService(env, SS0_ENV, CONSUMER_X_ROAD_CLIENT, PROVIDER_SERVICE_PATH),
                        CALL_TIMEOUT, CALL_POLL_INTERVAL));

        and("ss1 recorded a finalized negotiation whose counterparty id carries the context id decoded exactly once", () ->
                awaitProviderNegotiationWithDecodedCounterparty(ss1Assertions, negotiationsBefore));
    }

    /**
     * Waits for the member context's membership credential to be ISSUED and its member DID to be PUBLISHED
     * (the context's {@code publication_state}; the DSP base URL is the server-level state). Stops at once
     * on a credential {@code ERROR} or a {@code REJECTED} publication, reporting the context's
     * {@code publication_reason}.
     */
    private void awaitMemberCredentialIssuedAndDidPublished(String ss0BaseUrl, AdminSession ss0) {
        var context = awaitMemberContext(ss0BaseUrl, ss0, MEMBER_CTX_ID,
                "credential of %s is ISSUED and its DID is PUBLISHED".formatted(MEMBER_CTX_ID),
                member -> isFailed(member) || isIssuedAndPublished(member));
        assertThat(isFailed(context))
                .as("member context %s reached a terminal failure state: credential_status=%s, publication_state=%s, "
                                + "publication_reason=%s", MEMBER_CTX_ID, context.get("credential_status"),
                        context.get("publication_state"), context.get("publication_reason"))
                .isFalse();
    }

    private static boolean isFailed(Map<String, Object> member) {
        return CREDENTIAL_STATUS_ERROR.equals(member.get("credential_status"))
                || PUBLICATION_STATE_REJECTED.equals(member.get("publication_state"));
    }

    private static boolean isIssuedAndPublished(Map<String, Object> member) {
        return CREDENTIAL_STATUS_ISSUED.equals(member.get("credential_status"))
                && PUBLICATION_STATE_PUBLISHED.equals(member.get("publication_state"));
    }

    /** Retried because ss1 can grant access only once its global configuration lists the new subsystem. */
    private void awaitGrant(String ss1BaseUrl, AdminSession ss1) {
        Awaitility.await()
                .pollInterval(GRANT_POLL_INTERVAL)
                .timeout(GRANT_TIMEOUT)
                .untilAsserted(() -> grantConsumerAccessRights(
                        ss1BaseUrl, ss1, PROVIDER_CLIENT_ID, CONSUMER_CLIENT_ID, PROVIDER_SERVICE_CODE));
    }

    /**
     * ss1 stores the consumer's DID, taken from its token, as the counterparty id and the consumer's callback
     * address as sent. The callback must carry the context id with its percent signs encoded once more, so that
     * ss1's single decode of the path segment yields the context id.
     */
    private void awaitProviderNegotiationWithDecodedCounterparty(DspNegotiationDbAssertions ss1Assertions,
                                                                  long createdAfter) {
        ss1Assertions.awaitProviderNegotiationsCreatedAfter(PROVIDER_COUNTERPARTY_ID_LIKE, createdAfter, negotiations -> {
            assertThat(negotiations).as("ss1 PROVIDER negotiations of this run with a DEV:COM:222* counterparty").isNotEmpty();
            assertThat(negotiations)
                    .as("counterparty ids must all end with %s", CONSUMER_DID_SUFFIX)
                    .allMatch(negotiation -> negotiation.counterpartyId().endsWith(CONSUMER_DID_SUFFIX));
            assertThat(negotiations)
                    .as("callback addresses must all contain %s", CONSUMER_CALLBACK_SEGMENT)
                    .allMatch(negotiation -> negotiation.counterpartyAddress().contains(CONSUMER_CALLBACK_SEGMENT));
            assertThat(negotiations)
                    .as("a FINALIZED negotiation with the consumer")
                    .anyMatch(DspNegotiationDbAssertions.ProviderNegotiation::isFinalized);
        });
    }
}

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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Same-SS dataspace-protocol self-call: ss0 reaches its own {@code TestService} through its own proxy, so ss0
 * plays both the consumer and the provider role for one exchange. The consumer negotiates as the sender
 * member's derived participant context and dials the provider member's derived context (XRDADR-41 derivation)
 * — and this is a same-member call, so both sides ride one context ({@code DEV:COM:1234}) and the store's
 * composite-key upsert converges their agreement copies into one shared row. Proves that derivation and the
 * contract-negotiation store's converge path work together on a live stack, not just at the unit/store level,
 * by asserting the resulting negotiation and agreement rows directly in the ds-control-plane database — the
 * one scenario in this suite that reaches into DSP record-level state beyond
 * {@link SsProxyMessageFlowTest}'s counterparty-identity check, and the only live assertion of the converge
 * path now that distinct-member exchanges keep per-context copies
 * ({@link SsProxyDspRuntimeMemberTest} asserts that variant).
 *
 * <p>Only k8s and LXD run the dataspace protocol stack; the Compose facade does not implement
 * {@link DsControlPlaneDbOps}, so this scenario self-skips there via {@link Assumptions}.
 *
 * <p>Runs after {@link SsMessagelogArchiveTest}: its self-call traffic on ss0 would otherwise be counted by
 * that class's exact pre-archive messagelog assertions.
 *
 * <p><b>Reuse tolerance.</b> The proxy's DSP asset-access cache (Caffeine, {@code ProxyConfigKeys}'s
 * {@code DSP_CACHE_DEFAULT_TTL}, 5 minutes) plus ordinary EDC agreement reuse mean a self-negotiation for a given
 * (participant, asset) pair is only ever observable <i>once</i> per cache/agreement lifetime — a second call
 * inside that window reuses the existing agreement and transfer without touching the negotiation store at all.
 * So this test does not require a <i>freshly created</i> negotiation pair; {@link DspNegotiationDbAssertions}
 * identifies the self-negotiation structurally instead, by the pair shape scoped to this scenario's own
 * {@link #ASSET_ID}. Picking the pair with the latest activity still catches a freshly negotiated run; picking
 * the same pair on a warm-cache rerun is exactly the end-state proof this scenario exists to make, so reuse is
 * a pass, not a false green — a stalled or non-FINALIZED pair, or duplicate agreement copies, still fail
 * either way.
 */
@DisplayName("SS proxy - same-SS dataspace self-call")
@Order(300)
@SuppressWarnings("checkstyle:magicnumber")
class SsProxyDspSelfCallTest extends E2eTest {

    private static final String SELF_CALL_ENV = "ss0";
    private static final String SELF_CALL_X_ROAD_CLIENT = "DEV/COM/1234/TestService";
    private static final String SELF_CALL_SERVICE_PATH = "/r1/DEV/COM/1234/TestService/mock1";
    private static final String REST_REQUEST_BODY = """
            {"data": 1.0, "service": "random"}
            """;

    /**
     * The DSP asset id for this scenario's call: {@link #SELF_CALL_X_ROAD_CLIENT}'s service identifier plus
     * {@link #SELF_CALL_SERVICE_PATH}'s endpoint, colon-joined — the full deterministic form confirmed live
     * against {@code edc_contract_agreement.asset_id}. Matched with full-string equality so the same service
     * name under another member or instance can never satisfy the group query.
     */
    private static final String ASSET_ID = "DEV:COM:1234:TestService:mock1";

    /** The ctx-id the consumer side negotiates as: the sender member's derived context. */
    private static final String CONSUMER_MEMBER_CTX_ID = "DEV:COM:1234";

    @Test
    @DisplayName("Self-call converges its consumer and provider negotiation onto one agreement, and the transfer succeeds")
    void selfCallConvergesOntoOneAgreementAndTransfers(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; same-SS self-call is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));
        var dspAssertions = new DspNegotiationDbAssertions((DsControlPlaneDbOps) env,
                SELF_CALL_ENV, ASSET_ID, CONSUMER_MEMBER_CTX_ID, CONSUMER_MEMBER_CTX_ID);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var response = given("a REST request is sent from TestService to itself via the ss0 proxy", () ->
                sendSelfCallRequest(env));

        then("the response is 200 with the expected POST service message", () ->
                response.statusCode(200).body("message", equalTo("Hello, world from POST service!")));

        var wireAgreementId = then("the self-negotiation completes: a CONSUMER and a PROVIDER row, both on the "
                + "member's own context, share one wire agreement", () ->
                dspAssertions.awaitNegotiationPair());

        and("the two sides' agreement copies converge onto one edc_contract_agreement row on that context", () ->
                dspAssertions.assertPerContextAgreementCopies(wireAgreementId));

        and("the transfer over that agreement succeeds", () ->
                dspAssertions.awaitTransferSucceeded(wireAgreementId));
    }

    private ValidatableResponse sendSelfCallRequest(E2eEnvironment env) {
        var mapping = env.getContainerMapping(SELF_CALL_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        return RestAssuredFactory.given()
                .body(REST_REQUEST_BODY)
                .header("Content-Type", "application/json")
                .header("x-road-client", SELF_CALL_X_ROAD_CLIENT)
                .post("http://%s:%s%s".formatted(mapping.host(), mapping.port(), SELF_CALL_SERVICE_PATH))
                .then();
    }

}

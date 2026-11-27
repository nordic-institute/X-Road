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

package org.niis.xroad.edc.issuerdata;

import org.eclipse.edc.connector.controlplane.services.protocol.ProtocolTokenValidatorImpl;
import org.eclipse.edc.connector.core.profile.DataspaceProfileContextRegistryImpl;
import org.eclipse.edc.controlplane.ProtocolRemoteMessage;
import org.eclipse.edc.iam.decentralizedclaims.service.DcpIdentityService;
import org.eclipse.edc.iam.decentralizedclaims.service.verification.MultiFormatPresentationVerifier;
import org.eclipse.edc.iam.verifiablecredentials.VerifiableCredentialValidationServiceImpl;
import org.eclipse.edc.iam.verifiablecredentials.revocation.RevocationServiceRegistryImpl;
import org.eclipse.edc.iam.verifiablecredentials.rules.IsNotRevoked;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiablePresentation;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiablePresentationContainer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.jsonld.spi.JsonLdNamespace;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.context.request.spi.RequestScopeExtractorRegistry;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.protocol.spi.ProtocolVersion;
import org.eclipse.edc.protocol.spi.TrustedIssuer;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.token.TokenValidationRulesRegistryImpl;
import org.eclipse.edc.verifiablecredentials.jwt.JwtPresentationVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.test.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_CREDENTIAL;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_INDEX;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_PURPOSE;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_SIZE;
import static org.eclipse.edc.verifiablecredentials.jwt.Constants.JWT_VC_TOKEN_CONTEXT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives a credential through the EDC chain that answers a protocol request: the protocol token validator, the DCP
 * identity service, the credential validation service with the JWT verifier and the stock revocation check. Only the
 * peer-facing inputs are stubbed: the presentation request and the self-issued token. The credential is verified as a
 * standalone credential JWT.
 */
class RevocationOutageVerdictCanaryTest {

    private static final String INSTANCE = "DEV";
    private static final String PROFILE_NAME = "dsp-2025-1";
    private static final String OWN_DID = "did:web:ss1.example.org%3A7183:v1:DEV:COM:111";
    private static final String HOLDER_DID = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final String OTHER_ISSUER = "did:web:cs1.example.org%3A6183:issuer";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);
    private static final Duration DOWNLOAD_WAIT = Duration.ofSeconds(10);
    private static final Duration MAXIMUM_AGE = Duration.ofSeconds(120);
    private static final Duration PAST_THE_DEADLINE = Duration.ofMinutes(10);
    private static final int REVOKED_INDEX = 10;
    private static final int CLEAR_INDEX = 15;

    private final MutableClock clock = new MutableClock(START);
    private final GlobalConfProvider globalConf = mock(GlobalConfProvider.class);
    private final ExecutorService refreshPool = Executors.newCachedThreadPool();
    private final AtomicReference<VerifiablePresentationContainer> nextPresentation = new AtomicReference<>();
    private FakeIssuer issuer;
    private String credentialIssuerDid;

    @BeforeEach
    void setUp() {
        issuer = new FakeIssuer();
        issuer.publishStatusList(Set.of(REVOKED_INDEX), null);
        credentialIssuerDid = issuer.did();
        when(globalConf.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConf.getCredentialIssuerDids(INSTANCE)).thenReturn(Set.of(issuer.did()));
    }

    @AfterEach
    void tearDown() {
        refreshPool.shutdownNow();
        issuer.close();
    }

    @Test
    void shouldAcceptClearCredential() {
        var validator = validatorTrusting(issuer.did());

        var verdict = verify(validator, presentation(CLEAR_INDEX));

        assertThat(verdict.succeeded()).isTrue();
    }

    @Test
    void shouldAnswerUnavailableWhenStatusListIsDownPastTheDeadline() {
        var validator = validatorTrusting(issuer.did());
        assertThat(verify(validator, presentation(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.statusListDown(true);
        clock.set(START.plus(PAST_THE_DEADLINE));

        var verdict = verify(validator, presentation(CLEAR_INDEX));

        assertThat(verdict.failed()).isTrue();
        assertThat(verdict.getFailure().getReason()).isEqualTo(ServiceFailure.Reason.UNAVAILABLE);
    }

    @Test
    void shouldAnswerUnavailableWhenIssuerIsDownPastTheDeadlineOfTheIssuerDid() {
        var validator = validatorTrusting(issuer.did());
        assertThat(verify(validator, presentation(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.down(true);
        clock.set(START.plus(PAST_THE_DEADLINE));

        var verdict = verify(validator, presentation(CLEAR_INDEX));

        assertThat(verdict.failed()).isTrue();
        assertThat(verdict.getFailure().getReason()).isEqualTo(ServiceFailure.Reason.UNAVAILABLE);
    }

    @Test
    void shouldAnswerUnavailableWhenStatusListIsDownAndTheLastGoodCopyEndsWithinTheExpiryMargin() {
        var validator = validatorTrusting(issuer.did());
        assertThat(verify(validator, presentation(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.statusListDown(true);
        clock.set(START.plus(CACHE_TIME).plus(MAXIMUM_AGE).minusSeconds(4));

        var verdict = verify(validator, presentation(CLEAR_INDEX));

        assertThat(verdict.failed()).isTrue();
        assertThat(verdict.getFailure().getReason()).isEqualTo(ServiceFailure.Reason.UNAVAILABLE);
    }

    @Test
    void shouldAcceptClearCredentialWhenStatusListIsDownAndTheLastGoodCopyEndsBeyondTheExpiryMargin() {
        var validator = validatorTrusting(issuer.did());
        assertThat(verify(validator, presentation(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.statusListDown(true);
        clock.set(START.plus(CACHE_TIME).plus(MAXIMUM_AGE).minusSeconds(6));

        var verdict = verify(validator, presentation(CLEAR_INDEX));

        assertThat(verdict.succeeded()).isTrue();
    }

    @Test
    void shouldRefuseRevokedCredentialForGood() {
        var validator = validatorTrusting(issuer.did());

        var verdict = verify(validator, presentation(REVOKED_INDEX));

        assertThat(verdict.failed()).isTrue();
        assertThat(verdict.getFailure().getReason()).isEqualTo(ServiceFailure.Reason.UNAUTHORIZED);
    }

    @Test
    void shouldRefuseRevokedCredentialForGoodWhileLastGoodCopyIsServedDuringOutage() {
        var validator = validatorTrusting(issuer.did());
        assertThat(verify(validator, presentation(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.statusListDown(true);
        clock.set(START.plus(CACHE_TIME).plusSeconds(30));

        var verdict = verify(validator, presentation(REVOKED_INDEX));

        assertThat(verdict.failed()).isTrue();
        assertThat(verdict.getFailure().getReason()).isEqualTo(ServiceFailure.Reason.UNAUTHORIZED);
    }

    @Test
    void shouldRefuseForGoodWhenIssuerAnswersInvalidStatusList() {
        var validator = validatorTrusting(issuer.did());
        issuer.statusListRefused(true);

        var verdict = verify(validator, presentation(CLEAR_INDEX));

        assertThat(verdict.failed()).isTrue();
        assertThat(verdict.getFailure().getReason()).isEqualTo(ServiceFailure.Reason.UNAUTHORIZED);
    }

    @Test
    void shouldNotDownloadStatusListOfCredentialFromUntrustedIssuer() {
        when(globalConf.getCredentialIssuerDids(INSTANCE)).thenReturn(Set.of(OTHER_ISSUER));
        var validator = validatorTrusting(OTHER_ISSUER);

        var verdict = verify(validator, presentation(CLEAR_INDEX));

        assertThat(verdict.failed()).isTrue();
        assertThat(verdict.getFailure().getReason()).isEqualTo(ServiceFailure.Reason.UNAUTHORIZED);
        assertThat(issuer.statusListRequests()).isZero();
    }

    private ProtocolTokenValidatorImpl validatorTrusting(String trustedIssuerDid) {
        var store = new InMemoryIssuerDataStore();
        var stack = EdcTestStack.create(didFetcher -> new IssuerWebDidResolver(
                TestSources.source(didFetcher, clock, refreshPool, CACHE_TIME, DOWNLOAD_WAIT,
                        () -> Result.success(MAXIMUM_AGE), store), globalConf, mock(Monitor.class)));
        var contentTypes = List.of("*/*");
        var fetcher = new BitstringStatusListFetcher(stack.mapper(), contentTypes, stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver());
        var source = TestSources.source(fetcher, clock, refreshPool, CACHE_TIME, DOWNLOAD_WAIT,
                () -> Result.success(MAXIMUM_AGE), store);

        var revocationServices = new RevocationServiceRegistryImpl(mock(Monitor.class));
        revocationServices.addService(BitstringStatusListStatus.TYPE, new XRoadBitstringStatusListRevocationService(
                stack.mapper(), contentTypes, stack.httpClient(), stack.tokenValidationService(),
                stack.didPublicKeyResolver(), source));
        var rules = new TokenValidationRulesRegistryImpl();
        rules.addRule(JWT_VC_TOKEN_CONTEXT, new IssuerStatusListAvailabilityRule(globalConf, source));
        var jwtVerifier = new JwtPresentationVerifier(new JacksonTypeManager(), "dcp", stack.tokenValidationService(),
                rules, stack.didPublicKeyResolver());
        var credentialValidation = new VerifiableCredentialValidationServiceImpl(
                new MultiFormatPresentationVerifier(jwtVerifier), List.of(new IsNotRevoked(revocationServices)));

        var identityService = new DcpIdentityService(null, participantContextId -> OWN_DID,
                (participantContextId, token) -> Result.success(selfIssuedToken()),
                (participantContextId, ownDid, counterPartyDid, counterPartyToken, scopes) ->
                        Result.success(List.of(nextPresentation.get())),
                credentials -> Result.success(ClaimToken.Builder.newInstance().build()), credentialValidation);

        var profiles = new DataspaceProfileContextRegistryImpl();
        profiles.registerDefault(profile(trustedIssuerDid));
        var scopeExtractors = mock(RequestScopeExtractorRegistry.class);
        when(scopeExtractors.extractScopes(any(), any())).thenReturn(Result.success(Set.of()));
        var agents = mock(ParticipantAgentService.class);
        when(agents.createFor(any(), any())).thenReturn(mock(ParticipantAgent.class));
        return new ProtocolTokenValidatorImpl(identityService, scopeExtractors, mock(Monitor.class), agents, profiles);
    }

    private VerifiablePresentationContainer presentation(int statusIndex) {
        var credentialJwt = issuer.issueCredential(HOLDER_DID, statusIndex);
        var credential = VerifiableCredential.Builder.newInstance()
                .type("VerifiableCredential")
                .type("MembershipCredential")
                .issuer(new Issuer(credentialIssuerDid))
                .issuanceDate(START)
                .credentialSubject(CredentialSubject.Builder.newInstance().id(HOLDER_DID).claim("key", "value").build())
                .credentialStatus(new CredentialStatus(issuer.statusListUrl() + "#" + statusIndex,
                        BitstringStatusListStatus.TYPE,
                        Map.of(STATUS_LIST_PURPOSE, "revocation",
                                STATUS_LIST_INDEX, statusIndex,
                                STATUS_LIST_SIZE, 1,
                                STATUS_LIST_CREDENTIAL, issuer.statusListUrl())))
                .build();
        var presentation = VerifiablePresentation.Builder.newInstance()
                .type("VerifiablePresentation")
                .holder(HOLDER_DID)
                .credential(credential)
                .build();
        return new VerifiablePresentationContainer(credentialJwt, CredentialFormat.VC1_0_JWT, presentation);
    }

    private ServiceResult<ParticipantAgent> verify(ProtocolTokenValidatorImpl validator,
                                                   VerifiablePresentationContainer presentation) {
        nextPresentation.set(presentation);
        var participantContext = ParticipantContext.Builder.newInstance()
                .id("participant-context").identity(OWN_DID).build();
        var message = mock(ProtocolRemoteMessage.class);
        when(message.getProtocol()).thenReturn(PROFILE_NAME);
        return validator.verify(participantContext, TokenRepresentation.Builder.newInstance().token("Bearer si-token").build(),
                Policy.Builder.newInstance().build(), message);
    }

    private static ClaimToken selfIssuedToken() {
        return ClaimToken.Builder.newInstance().claim("iss", HOLDER_DID).claim("token", "access-token").build();
    }

    private static DataspaceProfileContext profile(String trustedIssuerDid) {
        return new DataspaceProfileContext(PROFILE_NAME,
                new ProtocolVersion("2025-1", "/" + PROFILE_NAME, "HTTPS"),
                () -> "https://localhost/dsp/" + PROFILE_NAME,
                claimToken -> "participant",
                new JsonLdNamespace("https://w3id.org/dspace/2025/1/"),
                List.of(),
                List.of(TrustedIssuer.Builder.newInstance().id(trustedIssuerDid)
                        .supportedTypes(List.of(TrustedIssuer.WILDCARD_TYPE)).build()));
    }
}

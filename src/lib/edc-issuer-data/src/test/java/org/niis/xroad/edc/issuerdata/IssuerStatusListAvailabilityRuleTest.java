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

import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.result.Result;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.BITSTRING_STATUS_LIST_CREDENTIAL_LITERAL;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_CREDENTIAL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IssuerStatusListAvailabilityRuleTest {

    private static final String INSTANCE = "DEV";
    private static final String OTHER_ISSUER = "did:web:cs1.example.org%3A6183:issuer";
    private static final String OTHER_STATUS_LIST = "https://cs1.example.org:6183/issuer/statuslist/2";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);
    private static final Duration DOWNLOAD_WAIT = Duration.ofSeconds(10);

    private final MutableClock clock = new MutableClock(START);
    private final GlobalConfProvider globalConf = mock(GlobalConfProvider.class);
    private final ExecutorService refreshPool = Executors.newCachedThreadPool();
    private FakeIssuer issuer;
    private IssuerStatusListAvailabilityRule rule;

    @BeforeEach
    void setUp() {
        issuer = new FakeIssuer();
        issuer.publishStatusList(Set.of(), null);
        when(globalConf.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConf.getCredentialIssuerDids(INSTANCE)).thenReturn(Set.of(issuer.did()));
        var stack = EdcTestStack.create();
        var store = new InMemoryIssuerDataStore();
        var fetcher = new BitstringStatusListFetcher(stack.mapper(), List.of("*/*"), stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver());
        var source = TestSources.source(fetcher, clock, refreshPool, CACHE_TIME, DOWNLOAD_WAIT,
                () -> Result.success(Duration.ofSeconds(120)), store);
        rule = new IssuerStatusListAvailabilityRule(globalConf, source);
    }

    @AfterEach
    void tearDown() {
        refreshPool.shutdownNow();
        issuer.close();
    }

    @Test
    void shouldSucceedWhenStatusListIsUsable() {
        var result = rule.checkRule(credentialToken(issuer.did(), issuer.statusListUrl()), Map.of());

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void shouldFailRetryablyWhenIssuerDoesNotAnswerAndNoCopyExists() {
        issuer.statusListDown(true);

        var result = rule.checkRule(credentialToken(issuer.did(), issuer.statusListUrl()), Map.of());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void shouldFailForGoodWhenIssuerAnswersInvalidListAndNoCopyExists() {
        issuer.statusListRefused(true);

        var result = rule.checkRule(credentialToken(issuer.did(), issuer.statusListUrl()), Map.of());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void shouldSucceedWhileLastGoodCopyIsServedDuringOutage() {
        assertThat(rule.checkRule(credentialToken(issuer.did(), issuer.statusListUrl()), Map.of()).succeeded()).isTrue();
        issuer.statusListDown(true);
        clock.set(START.plus(CACHE_TIME).plusSeconds(30));

        var result = rule.checkRule(credentialToken(issuer.did(), issuer.statusListUrl()), Map.of());

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void shouldFailForGoodWhenOneOfSeveralStatusListsIsInvalidAndAnotherUnavailable() {
        var source = mockSource();
        when(source.lookup(eq(issuer.statusListUrl()), any(Duration.class))).thenReturn(Result.retryableFailure("issuer down"));
        when(source.lookup(eq(OTHER_STATUS_LIST), any(Duration.class))).thenReturn(Result.failure("not a statuslist"));
        var token = credentialToken(issuer.did(), List.of(statusEntry(issuer.statusListUrl()), statusEntry(OTHER_STATUS_LIST)));

        var result = new IssuerStatusListAvailabilityRule(globalConf, source).checkRule(token, Map.of());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void shouldNotAccessSourceForIssuerThatIsNotACredentialIssuerOfTheInstance() {
        var source = mockSource();
        when(globalConf.getCredentialIssuerDids(INSTANCE)).thenReturn(Set.of(OTHER_ISSUER));

        var result = new IssuerStatusListAvailabilityRule(globalConf, source)
                .checkRule(credentialToken(issuer.did(), issuer.statusListUrl()), Map.of());

        assertThat(result.succeeded()).isTrue();
        verifyNoInteractions(source);
        assertThat(issuer.statusListRequests()).isZero();
    }

    @Test
    void shouldNotAccessSourceWhenKeyIdDoesNotStartWithIssuerAndHash() {
        var source = mockSource();
        var token = ClaimToken.Builder.newInstance()
                .headers(Map.of("kid", OTHER_ISSUER + "#key-1"))
                .claim("iss", issuer.did())
                .claim("vc", credentialStatus(statusEntry(issuer.statusListUrl())))
                .build();

        var result = new IssuerStatusListAvailabilityRule(globalConf, source).checkRule(token, Map.of());

        assertThat(result.succeeded()).isTrue();
        verifyNoInteractions(source);
        assertThat(issuer.statusListRequests()).isZero();
    }

    @Test
    void shouldFailRetryablyWithoutAccessingSourceWhenGlobalConfIsNotReadable() {
        var source = mockSource();
        when(globalConf.getCredentialIssuerDids(INSTANCE)).thenThrow(new IllegalStateException("globalconf missing"));

        var result = new IssuerStatusListAvailabilityRule(globalConf, source)
                .checkRule(credentialToken(issuer.did(), issuer.statusListUrl()), Map.of());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
        verifyNoInteractions(source);
    }

    @Test
    void shouldSucceedWithoutAccessingSourceWhenCredentialHasNoStatusList() {
        var source = mockSource();
        var token = ClaimToken.Builder.newInstance()
                .headers(Map.of("kid", issuer.did() + "#key-1"))
                .claim("iss", issuer.did())
                .claim("vc", Map.of("type", List.of("VerifiableCredential")))
                .build();

        var result = new IssuerStatusListAvailabilityRule(globalConf, source).checkRule(token, Map.of());

        assertThat(result.succeeded()).isTrue();
        verifyNoInteractions(source);
    }

    @Test
    void shouldReadStatusListOfEntryWithListValuedType() {
        issuer.statusListDown(true);
        var entry = Map.of("type", List.of("VerifiableCredentialStatus", BitstringStatusListStatus.TYPE),
                STATUS_LIST_CREDENTIAL, issuer.statusListUrl());

        var result = rule.checkRule(credentialToken(issuer.did(), entry), Map.of());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void shouldReadStatusListGivenAsObjectWithId() {
        issuer.statusListDown(true);
        var entry = Map.of("type", BitstringStatusListStatus.TYPE,
                BITSTRING_STATUS_LIST_CREDENTIAL_LITERAL, Map.of("@id", issuer.statusListUrl()));

        var result = rule.checkRule(credentialToken(issuer.did(), entry), Map.of());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void shouldReadStatusListGivenUnderNonPrefixedKey() {
        issuer.statusListDown(true);
        var entry = Map.of("type", BitstringStatusListStatus.TYPE,
                BITSTRING_STATUS_LIST_CREDENTIAL_LITERAL, issuer.statusListUrl());

        var result = rule.checkRule(credentialToken(issuer.did(), entry), Map.of());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static LastGoodSource<BitstringStatusListCredential> mockSource() {
        return mock(LastGoodSource.class);
    }

    private static ClaimToken credentialToken(String issuerDid, String statusListUrl) {
        return credentialToken(issuerDid, statusEntry(statusListUrl));
    }

    private static ClaimToken credentialToken(String issuerDid, Object credentialStatus) {
        return ClaimToken.Builder.newInstance()
                .headers(Map.of("kid", issuerDid + "#key-1"))
                .claim("iss", issuerDid)
                .claim("vc", credentialStatus(credentialStatus))
                .build();
    }

    private static Map<String, Object> credentialStatus(Object entries) {
        return Map.of("credentialStatus", entries);
    }

    private static Map<String, Object> statusEntry(String statusListUrl) {
        return Map.of("type", BitstringStatusListStatus.TYPE, STATUS_LIST_CREDENTIAL, statusListUrl);
    }
}

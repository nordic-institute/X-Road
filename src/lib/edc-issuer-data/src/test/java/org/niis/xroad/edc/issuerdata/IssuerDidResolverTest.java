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

import com.github.benmanes.caffeine.cache.Ticker;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.resolution.DidResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.edc.issuerdata.TestSettings.settings;

class IssuerDidResolverTest {
    private static final String INSTANCE = "DEV";
    private static final String PARTICIPANT_DID = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final Duration MAXIMUM_AGE = Duration.ofSeconds(60);
    private static final Duration WITHIN_DEADLINE = Duration.ofSeconds(30);
    private static final Duration PAST_DEADLINE = Duration.ofSeconds(56);
    private static final long MAXIMUM_ENTRIES = 32;
    private static final Duration IDLE_EXPIRY = Duration.ofMinutes(30);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T10:00:00Z"));
    private final IssuerDataStore store = mock(IssuerDataStore.class);
    private final GlobalConfProvider globalConf = mock(GlobalConfProvider.class);
    private final AtomicReference<IssuerDidResolver> issuerDidResolver = new AtomicReference<>();
    private FakeIssuer issuer;
    private EdcTestStack stack;

    @BeforeEach
    void setUp() {
        issuer = new FakeIssuer();
        when(store.write(any(), any(), any())).thenReturn(StoreResult.success());
        when(store.read(any(), any())).thenReturn(StoreResult.notFound("no row"));
        when(globalConf.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConf.getCredentialIssuerDids(INSTANCE)).thenReturn(Set.of(issuer.did()));
        stack = EdcTestStack.create((stockResolver, fetcher) -> {
            var didSource = newSource(fetcher);
            issuerDidResolver.set(new IssuerDidResolver(stockResolver, didSource, globalConf));
            return issuerDidResolver.get();
        });
    }

    @AfterEach
    void tearDown() {
        issuer.close();
    }

    @Test
    void shouldServeLastGoodDidDocumentWhileIssuerIsDownWithinMaximumAge() {
        assertThat(resolveIssuerDid().succeeded()).isTrue();
        issuer.down(true);
        var requestsBeforeOutage = issuer.didDocumentRequests();
        clock.advance(WITHIN_DEADLINE);

        var result = resolveIssuerDid();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent().getId()).isEqualTo(issuer.did());
        assertThat(issuer.didDocumentRequests()).isGreaterThan(requestsBeforeOutage);
    }

    @Test
    void shouldFailRetryablePastMaximumAgeWhileIssuerIsDown() {
        assertThat(resolveIssuerDid().succeeded()).isTrue();
        issuer.down(true);
        clock.advance(WITHIN_DEADLINE);
        assertThat(resolveIssuerDid().succeeded()).isTrue();
        clock.advance(PAST_DEADLINE.minus(WITHIN_DEADLINE));

        var result = resolveIssuerDid();

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void shouldGiveParticipantDidToTheStockResolverWithoutCopyOrStoredRow() {
        var stockResolver = mock(DidResolver.class);
        var document = DidDocument.Builder.newInstance().id(PARTICIPANT_DID).build();
        when(stockResolver.resolve(PARTICIPANT_DID)).thenReturn(Result.success(document))
                .thenReturn(Result.retryableFailure("participant did down"));
        var didSource = newSource(stack.didDocumentFetcher());
        var resolver = new IssuerDidResolver(stockResolver, didSource, globalConf);

        var first = resolver.resolve(PARTICIPANT_DID);
        clock.advance(WITHIN_DEADLINE);

        var second = resolver.resolve(PARTICIPANT_DID);

        assertThat(first.getContent()).isSameAs(document);
        assertThat(second.failed()).isTrue();
        assertThat(second.isRetryable()).isTrue();
        verify(stockResolver, times(2)).resolve(PARTICIPANT_DID);
        assertThat(didSource.cachedKeys()).isZero();
        verifyNoInteractions(store);
    }

    @Test
    void shouldGiveIssuerDidToTheStockResolverWhenGlobalConfIsUnreadable() {
        var stockResolver = mock(DidResolver.class);
        var document = DidDocument.Builder.newInstance().id(issuer.did()).build();
        when(stockResolver.resolve(issuer.did())).thenReturn(Result.success(document));
        when(globalConf.getInstanceIdentifier()).thenThrow(new IllegalStateException("no globalconf yet"));
        var resolver = new IssuerDidResolver(stockResolver, newSource(stack.didDocumentFetcher()), globalConf);

        var result = resolver.resolve(issuer.did());

        assertThat(result.getContent()).isSameAs(document);
        verifyNoInteractions(store);
    }

    @Test
    void shouldFailWithoutRetryWhenIssuerRefusesTheDidDocument() {
        issuer.didDocumentRefused(true);

        var result = resolveIssuerDid();

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void shouldNotLeakTheIssuerResponseBodyIntoTheFailure() {
        issuer.down(true);

        var result = resolveIssuerDid();

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).doesNotContain(FakeIssuer.ERROR_BODY);
    }

    @Test
    void shouldVerifyCredentialSignedWithKeyMissingFromLiveDocumentWhileLastGoodDocumentIsServed()
            throws ParseException, JOSEException {
        var keyId = issuer.did() + "#key-1";
        var servedDocument = issuer.didDocumentJson();
        assertThat(stack.didPublicKeyResolver().resolveKey(keyId).succeeded()).isTrue();
        issuer.publishDidDocumentWithOnlyKey("key-2");
        issuer.down(true);
        clock.advance(WITHIN_DEADLINE);

        var key = stack.didPublicKeyResolver().resolveKey(keyId);
        assertThat(key.succeeded()).isTrue();
        assertThat(signatureVerifies(issuer.issueCredential(PARTICIPANT_DID, 1), (ECPublicKey) key.getContent())).isTrue();
        assertThat(stack.didPublicKeyResolver().resolveKey(issuer.did() + "#key-2").failed()).isTrue();
        assertThat(storedDidPayload()).isEqualTo(servedDocument);
        var method = resolveIssuerDid().getContent().getVerificationMethod().getFirst();
        assertThat(method.getId()).isEqualTo(keyId);
        assertThat(method.getPublicKeyJwk()).isEqualTo(issuer.signingPublicJwk());
    }

    private String storedDidPayload() {
        var written = ArgumentCaptor.forClass(StoredIssuerData.class);
        verify(store, atLeastOnce()).write(eq(IssuerDataKind.DID), eq(issuer.did()), written.capture());
        return written.getAllValues().getFirst().payload();
    }

    private Result<DidDocument> resolveIssuerDid() {
        return issuerDidResolver.get().resolve(issuer.did());
    }

    private LastGoodSource<DidDocument> newSource(DidDocumentFetcher fetcher) {
        return new LastGoodSource<>(fetcher, clock, Ticker.disabledTicker(),
                Runnable::run, Duration.ZERO, settings(MAXIMUM_ENTRIES, IDLE_EXPIRY, Duration.ofSeconds(2)),
                () -> Result.success(MAXIMUM_AGE), store, mock(Monitor.class));
    }

    private static boolean signatureVerifies(String jwt, ECPublicKey key) throws ParseException, JOSEException {
        return SignedJWT.parse(jwt).verify(new ECDSAVerifier(key));
    }
}

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

import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.resolution.DidResolver;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.test.MutableClock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IssuerDataDidResolverRegistryTest {
    private static final String INSTANCE = "DEV";
    private static final String PARTICIPANT_DID = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final String OWN_ISSUER_KEY_DID = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK";
    private static final long CACHE_VALIDITY_MILLIS = 60_000;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T10:00:00Z"));
    private final InMemoryIssuerDataStore store = new InMemoryIssuerDataStore();
    private final GlobalConfProvider globalConf = mock(GlobalConfProvider.class);
    private final WebDidResolver stockResolver = mock(WebDidResolver.class);
    private final DidResolver keyResolver = mock(DidResolver.class);
    private final DidDocument participantDocument = DidDocument.Builder.newInstance().id(PARTICIPANT_DID).build();
    private final EdcTestStack stack = EdcTestStack.create();
    private final IssuerDataDidResolverRegistry registry =
            new IssuerDataDidResolverRegistry(50, CACHE_VALIDITY_MILLIS, Clock.systemUTC());
    private FakeIssuer issuer;
    private IssuerWebDidResolver issuerResolver;

    @BeforeEach
    void setUp() {
        issuer = new FakeIssuer();
        when(globalConf.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConf.getCredentialIssuerDids(INSTANCE)).thenReturn(Set.of(issuer.did(), OWN_ISSUER_KEY_DID));
        when(stockResolver.getMethod()).thenReturn("web");
        when(keyResolver.getMethod()).thenReturn("key");
        when(stockResolver.resolve(PARTICIPANT_DID)).thenReturn(Result.success(participantDocument));
        var didSource = TestSources.source(stack.didDocumentFetcher(), clock, Runnable::run, Duration.ZERO,
                Duration.ofSeconds(2), () -> Result.success(Duration.ofSeconds(60)), store);
        issuerResolver = new IssuerWebDidResolver(didSource, globalConf, mock(Monitor.class));
    }

    @AfterEach
    void tearDown() {
        issuer.close();
    }

    @Test
    void shouldCacheParticipantDidThroughTheStockPathWithoutIssuerCopyOrStoredRow() {
        registry.register(stockResolver);
        registry.register(issuerResolver);

        var first = registry.resolve(PARTICIPANT_DID);
        var second = registry.resolve(PARTICIPANT_DID);

        assertThat(first.getContent()).isSameAs(participantDocument);
        assertThat(second.getContent()).isSameAs(participantDocument);
        verify(stockResolver, times(1)).resolve(PARTICIPANT_DID);
        assertThat(issuer.didDocumentRequests()).isZero();
        assertThat(store.reads.get()).isZero();
        assertThat(store.writes.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldKeepParticipantResolverAndSupportIssuerDidInEitherRegistrationOrder(boolean issuerResolverFirst) {
        if (issuerResolverFirst) {
            registry.register(issuerResolver);
            registry.register(stockResolver);
        } else {
            registry.register(stockResolver);
            registry.register(issuerResolver);
        }

        assertThat(registry.isSupported(issuer.did())).isTrue();
        assertThat(registry.resolve(PARTICIPANT_DID).getContent()).isSameAs(participantDocument);
        assertThat(registry.resolve(issuer.did()).getContent().getId()).isEqualTo(issuer.did());
        assertThat(issuer.didDocumentRequests()).isEqualTo(1);
    }

    @Test
    void shouldSupportOwnIssuerDidWithoutStockResolverAndNotSupportParticipantDid() {
        registry.register(issuerResolver);

        assertThat(registry.isSupported(issuer.did())).isTrue();
        assertThat(registry.isSupported(PARTICIPANT_DID)).isFalse();
    }

    @Test
    void shouldTakeStockPathForOwnIssuerDidOfAnotherMethod() {
        var keyDocument = DidDocument.Builder.newInstance().id(OWN_ISSUER_KEY_DID).build();
        when(keyResolver.resolve(OWN_ISSUER_KEY_DID)).thenReturn(Result.success(keyDocument));
        registry.register(stockResolver);
        registry.register(keyResolver);
        registry.register(issuerResolver);
        var result = registry.resolve(OWN_ISSUER_KEY_DID);
        assertThat(issuerResolver.canResolve(OWN_ISSUER_KEY_DID)).isTrue();
        assertThat(result.getContent()).isSameAs(keyDocument);
        verify(keyResolver, times(1)).resolve(OWN_ISSUER_KEY_DID);
        assertThat(issuer.didDocumentRequests()).isZero();
        assertThat(store.reads.get()).isZero();
        assertThat(store.writes.get()).isZero();
    }
}

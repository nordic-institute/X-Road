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
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XRoadBitstringStatusListRevocationServiceTest {

    private static final String SUBJECT = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);
    private static final int REVOKED_INDEX = 10;
    private static final int CLEAR_INDEX = 15;
    private static final Duration REFRESH_WAIT = Duration.ofSeconds(10);
    private static final long MAXIMUM_ENTRIES = 128;
    private static final Duration IDLE_EXPIRY = Duration.ofSeconds(120);

    private final MutableClock clock = new MutableClock(START);
    private final AtomicReference<Result<Duration>> maximumAge = new AtomicReference<>();
    private final ExecutorService refreshPool = Executors.newCachedThreadPool();
    private FakeIssuer issuer;
    private XRoadBitstringStatusListRevocationService service;

    @BeforeEach
    void setUp() {
        issuer = new FakeIssuer();
        issuer.publishStatusList(Set.of(REVOKED_INDEX), null);
        maximumAge.set(Result.success(Duration.ofSeconds(120)));
        var stack = EdcTestStack.create();
        var contentTypes = List.of("*/*");
        var store = mock(IssuerDataStore.class);
        when(store.write(any(), any(), any())).thenReturn(StoreResult.success());
        when(store.read(any(), any())).thenReturn(StoreResult.notFound("no row"));
        var fetcher = new BitstringStatusListFetcher(stack.mapper(), contentTypes, stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver());
        var source = new LastGoodSource<>(fetcher, clock, Ticker.disabledTicker(), refreshPool,
                CACHE_TIME, MAXIMUM_ENTRIES, IDLE_EXPIRY, REFRESH_WAIT, maximumAge::get, store, mock(Monitor.class));
        service = new XRoadBitstringStatusListRevocationService(stack.mapper(), contentTypes, stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver(), source);
    }

    @AfterEach
    void tearDown() {
        refreshPool.shutdownNow();
        issuer.close();
    }

    @Test
    void shouldVerifyClearCredentialUntilDeadlineAndRefuseAfterWhenIssuerIsDown() {
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.down(true);

        clock.set(START.plus(CACHE_TIME).plusSeconds(100));
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();

        clock.set(START.plus(CACHE_TIME).plusSeconds(116));
        assertThat(service.checkValidity(status(CLEAR_INDEX)).failed()).isTrue();
    }

    @Test
    void shouldVerifyClearCredentialUntilDeadlineAndRefuseAfterWhenIssuerRefusesTheStatusList() {
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.statusListRefused(true);
        clock.set(START.plus(CACHE_TIME).plusSeconds(100));
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        clock.set(START.plus(CACHE_TIME).plusSeconds(116));
        assertThat(service.checkValidity(status(CLEAR_INDEX)).failed()).isTrue();
    }

    @Test
    void shouldRefuseRevokedCredentialWhileLastGoodCopyIsServed() {
        assertThat(service.checkValidity(status(REVOKED_INDEX)).failed()).isTrue();
        issuer.down(true);
        clock.set(START.plus(CACHE_TIME).plusSeconds(30));

        var result = service.checkValidity(status(REVOKED_INDEX));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("status at index " + REVOKED_INDEX + " is '1'");
    }

    @Test
    void shouldNotServeStatusListPastItsOwnValidUntilWithinMaximumAge() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        issuer.publishStatusList(Set.of(), START.plusSeconds(90));
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.down(true);

        clock.set(START.plusSeconds(80));
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();

        clock.set(START.plusSeconds(100));
        assertThat(service.checkValidity(status(CLEAR_INDEX)).failed()).isTrue();
    }

    @Test
    void shouldStoreNoStaleUseWhenMaximumAgeIsUnreadableAtWrite() {
        maximumAge.set(Result.failure("globalconf unreadable"));
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        maximumAge.set(Result.success(Duration.ofSeconds(120)));
        issuer.down(true);

        clock.set(START.plus(CACHE_TIME).plusSeconds(1));

        assertThat(service.checkValidity(status(CLEAR_INDEX)).failed()).isTrue();
    }

    @Test
    void shouldFailWithoutExceptionWhenMaximumAgeIsUnreadableAtCheck() {
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.down(true);
        maximumAge.set(Result.failure("globalconf unreadable"));
        clock.set(START.plus(CACHE_TIME).plusSeconds(1));

        var result = service.checkValidity(status(CLEAR_INDEX));

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldReadStatusListFromIssuerOncePerCacheTime() {
        service.checkValidity(status(CLEAR_INDEX));
        service.checkValidity(status(REVOKED_INDEX));

        assertThat(issuer.statusListRequests()).isEqualTo(1);
    }

    @Test
    void shouldRefuseCredentialRevokedAfterFirstCheckOnceCacheTimeHasPassed() {
        issuer.publishStatusList(Set.of(), null);
        assertThat(service.checkValidity(status(REVOKED_INDEX)).succeeded()).isTrue();
        issuer.publishStatusList(Set.of(REVOKED_INDEX), null);

        clock.set(START.plus(CACHE_TIME).minusSeconds(1));
        assertThat(service.checkValidity(status(REVOKED_INDEX)).succeeded()).isTrue();

        clock.set(START.plus(CACHE_TIME));
        var result = service.checkValidity(status(REVOKED_INDEX));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("status at index " + REVOKED_INDEX + " is '1'");
        assertThat(issuer.statusListRequests()).isEqualTo(2);
    }

    @Test
    void shouldAskIssuerOnceInOneBackoffWindowWhileItIsDown() {
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.down(true);
        clock.set(START.plus(CACHE_TIME).plusSeconds(1));
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        var requestsAfterFirstFailure = issuer.statusListRequests();

        for (var i = 0; i < 10; i++) {
            clock.advance(Duration.ofMillis(300));
            assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        }

        assertThat(issuer.statusListRequests()).isEqualTo(requestsAfterFirstFailure);
    }

    private CredentialStatus status(int index) {
        return new CredentialStatus(SUBJECT, BitstringStatusListStatus.TYPE,
                Map.of(STATUS_LIST_PURPOSE, "revocation",
                        STATUS_LIST_INDEX, index,
                        STATUS_LIST_SIZE, 1,
                        STATUS_LIST_CREDENTIAL, issuer.statusListUrl()));
    }
}

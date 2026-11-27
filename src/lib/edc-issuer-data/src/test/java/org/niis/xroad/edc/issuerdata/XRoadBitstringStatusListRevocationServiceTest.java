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

import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class XRoadBitstringStatusListRevocationServiceTest {

    private static final String SUBJECT = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);
    private static final int REVOKED_INDEX = 10;
    private static final int CLEAR_INDEX = 15;
    private static final Duration DOWNLOAD_WAIT = Duration.ofSeconds(10);

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
        var store = new InMemoryIssuerDataStore();
        var fetcher = new BitstringStatusListFetcher(stack.mapper(), contentTypes, stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver());
        var source = TestSources.source(fetcher, clock, refreshPool, CACHE_TIME, DOWNLOAD_WAIT, maximumAge::get, store);
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

        clock.set(START.plus(CACHE_TIME).plusSeconds(120));
        var pastDeadline = service.checkValidity(status(CLEAR_INDEX));
        assertThat(pastDeadline.failed()).isTrue();
        assertThat(pastDeadline.isRetryable()).isTrue();
    }

    @Test
    void shouldRefuseForGoodWhenIssuerRefusesTheStatusListPastTheDeadline() {
        assertThat(service.checkValidity(status(CLEAR_INDEX)).succeeded()).isTrue();
        issuer.statusListRefused(true);
        clock.set(START.plus(CACHE_TIME).plusSeconds(120));

        var result = service.checkValidity(status(CLEAR_INDEX));

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
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

    private CredentialStatus status(int index) {
        return new CredentialStatus(SUBJECT, BitstringStatusListStatus.TYPE,
                Map.of(STATUS_LIST_PURPOSE, "revocation",
                        STATUS_LIST_INDEX, index,
                        STATUS_LIST_SIZE, 1,
                        STATUS_LIST_CREDENTIAL, issuer.statusListUrl()));
    }
}

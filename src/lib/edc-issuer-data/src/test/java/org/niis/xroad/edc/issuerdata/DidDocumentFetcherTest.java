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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DidDocumentFetcherTest {

    private FakeIssuer issuer;
    private DidDocumentFetcher fetcher;

    @BeforeEach
    void setUp() {
        issuer = new FakeIssuer();
        fetcher = EdcTestStack.create().didDocumentFetcher();
    }

    @AfterEach
    void tearDown() {
        issuer.close();
    }

    @Test
    void shouldStampDownloadWithTheTimeTheRequestWasSentNotTheTimeTheAnswerArrived() {
        var answerDelay = Duration.ofMillis(200);
        issuer.answerAfter(answerDelay);
        var before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        var result = fetcher.download(issuer.did());

        var after = Instant.now();
        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent().requestSentAt()).isBetween(before, after.minus(answerDelay));
    }
    @Test
    void shouldReportRetryableFailureWhenTheResponseIsCutOffMidBody() {
        issuer.bodyDelivery(FakeIssuer.BodyDelivery.TRUNCATED);
        var result = fetcher.download(issuer.did());
        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429})
    void shouldReportRetryableFailureWhenIssuerAsksToTryAgainLater(int status) {
        issuer.answerWith(status);

        var result = fetcher.download(issuer.did());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 410})
    void shouldReportFinalFailureWhenIssuerRefusesTheRequest(int status) {
        issuer.answerWith(status);

        var result = fetcher.download(issuer.did());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void shouldReportRetryableFailureWithoutIssuerResponseBodyWhenIssuerAnswers503() {
        issuer.down(true);

        var result = fetcher.download(issuer.did());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getFailureMessages()).isNotEmpty().noneMatch(message -> message.contains(FakeIssuer.ERROR_BODY));
    }

    @Test
    void shouldReportFinalFailureWhenTheDocumentCannotBeRead() {
        var result = fetcher.parse(issuer.did(), "not a document");

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
    }
}

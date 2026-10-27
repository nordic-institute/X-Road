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

package org.niis.xroad.edc.extension.assetaccess.failure;

import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class FailureClassifierTest {
    @Test
    void noResponseIsATransportFault() {
        assertThat(FailureClassifier.classify(new EdcHttpClientException("no response", 0, null)))
                .isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void noResponseBehindAWrapperIsATransportFault() {
        assertThat(FailureClassifier.classify(new CompletionException(new EdcHttpClientException("no response", 0, null))))
                .isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void providerServerErrorIsOther() {
        assertThat(FailureClassifier.classify(new EdcHttpClientException("Server response was 503", 503, "unavailable")))
                .isEqualTo(FailureKind.OTHER);
    }

    @Test
    void localEdcFailureIsOther() {
        assertThat(FailureClassifier.classify(new EdcException("no dispatcher for the protocol"))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void stageTimeoutIsATransportFault() {
        assertThat(FailureClassifier.classify(new TimeoutException())).isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void stageTimeoutBehindAWrapperIsATransportFault() {
        assertThat(FailureClassifier.classify(new CompletionException(new TimeoutException())))
                .isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void anythingElseIsOther() {
        assertThat(FailureClassifier.classify(new IllegalStateException("boom"))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void clientErrorStatusResultIsAProviderRejection() {
        assertThat(FailureClassifier.classify(StatusResult.failure(ResponseStatus.FATAL_ERROR, "Counter Party responded with 403")))
                .isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @Test
    void retryableStatusResultIsOther() {
        assertThat(FailureClassifier.classify(StatusResult.failure(ResponseStatus.ERROR_RETRY, "Counter Party responded with 302")))
                .isEqualTo(FailureKind.OTHER);
    }
}

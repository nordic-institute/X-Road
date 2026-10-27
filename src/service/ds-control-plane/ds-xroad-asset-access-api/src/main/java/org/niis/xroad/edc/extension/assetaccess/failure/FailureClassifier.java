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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.spi.response.StatusResult;

import java.util.concurrent.TimeoutException;

/**
 * Maps an acquisition stage failure to a {@link FailureKind}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FailureClassifier {

    private static final int NO_RESPONSE_STATUS = 0;

    /**
     * A failed future from an EDC protocol request. The future fails with an {@link EdcHttpClientException}: status 0
     * when the provider gave no response, or the provider's 5xx status once retries end. EDC drops the underlying
     * {@code IOException}, so only the status tells them apart. A stage {@link TimeoutException} anywhere in the cause
     * chain is a transport fault. Local EDC failures and anything else are {@link FailureKind#OTHER}.
     */
    public static FailureKind classify(Throwable failure) {
        var httpFailure = ExceptionUtils.throwableOfType(failure, EdcHttpClientException.class);
        if (httpFailure != null) {
            return httpFailure.getStatusCode() == NO_RESPONSE_STATUS ? FailureKind.TRANSPORT_FAULT : FailureKind.OTHER;
        }
        return ExceptionUtils.indexOfType(failure, TimeoutException.class) >= 0 ? FailureKind.TRANSPORT_FAULT : FailureKind.OTHER;
    }

    /**
     * A failed {@link StatusResult}; it expects a failed result. EDC's DSP dispatcher turns only a 4xx answer into a
     * failed {@link StatusResult}, as a fatal error.
     */
    public static FailureKind classify(StatusResult<?> result) {
        return result.fatalError() ? FailureKind.PROVIDER_REJECTION : FailureKind.OTHER;
    }
}

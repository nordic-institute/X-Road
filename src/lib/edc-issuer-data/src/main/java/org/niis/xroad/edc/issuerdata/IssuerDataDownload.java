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

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.result.Result;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

/**
 * Runs the HTTP request of an {@link IssuerDataFetcher} and sorts the outcome. A failure is retryable when the issuer
 * did not give a usable answer (no answer, a server error, a timeout or a rate limit) and final when it refused.
 */
@RequiredArgsConstructor
final class IssuerDataDownload {

    private static final Set<Integer> REFUSAL_STATUS_CODES_EXCEPTIONS = Set.of(408, 429);

    private final EdcHttpClient httpClient;

    /**
     * Sends the request without {@code FallbackFactories.retryWhenStatusNot2xxOr4xx()}. Without a fallback the client
     * returns the response for every status. With it, a 1xx, 3xx or 5xx becomes an {@code EdcHttpClientException} (an
     * {@code EdcException}), which only serves to start a retry, and this client does not retry. The status is sorted
     * here, 408 and 429 included.
     *
     * @param request what to ask the issuer
     * @param subject what is downloaded, for failure messages
     */
    @Nonnull
    Result<IssuerDataFetcher.Download> get(Request request, String subject) {
        try (var response = httpClient.execute(request)) {
            return readBody(subject, response);
        } catch (IOException | RuntimeException e) {
            return Result.retryableFailure("Failed to download %s: %s".formatted(subject, e.getMessage()));
        }
    }

    private static Result<IssuerDataFetcher.Download> readBody(String subject, Response response) {
        if (!response.isSuccessful()) {
            var message = "Failed to download %s: HTTP %d".formatted(subject, response.code());
            return isRefusal(response.code())
                    ? Result.failure(message)
                    : Result.retryableFailure(message);
        }
        try {
            var payload = response.body().string();
            return Result.success(new IssuerDataFetcher.Download(payload,
                    Instant.ofEpochMilli(response.sentRequestAtMillis())));
        } catch (IOException e) {
            return Result.retryableFailure("Failed to read the response while downloading %s: %s"
                    .formatted(subject, e.getMessage()));
        }
    }

    private static boolean isRefusal(int status) {
        return status >= HTTP_BAD_REQUEST && status < HTTP_INTERNAL_ERROR && !REFUSAL_STATUS_CODES_EXCEPTIONS.contains(status);
    }
}

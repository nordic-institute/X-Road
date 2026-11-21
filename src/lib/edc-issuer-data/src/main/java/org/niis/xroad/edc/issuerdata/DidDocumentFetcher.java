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
import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.spi.result.Result;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.eclipse.edc.http.spi.FallbackFactories.retryWhenStatusNot2xxOr4xx;

/**
 * DID documents, downloaded from their did:web URL and stored as received. A document is read the way
 * the stock resolver reads a response.
 */
@RequiredArgsConstructor
public class DidDocumentFetcher implements IssuerDataFetcher<DidDocument> {

    private final EdcHttpClient httpClient;
    private final UnaryOperator<String> didUrlResolver;
    private final ObjectMapper mapper;

    @Override
    public IssuerDataKind kind() {
        return IssuerDataKind.DID;
    }

    @Override
    @Nonnull
    public Result<Download> download(String did) {
        Request request;
        try {
            request = new Request.Builder().url(didUrlResolver.apply(did)).get().build();
        } catch (IllegalArgumentException e) {
            return Result.failure("Invalid DID key: " + e.getMessage());
        }
        try (var response = httpClient.execute(request, List.of(retryWhenStatusNot2xxOr4xx()))) {
            return readBody(did, response);
        } catch (EdcHttpClientException e) {
            var reason = e.getStatusCode() > 0 ? "HTTP " + e.getStatusCode() : e.getMessage();
            return Result.retryableFailure("Failed to download DID document of %s: %s".formatted(did, reason));
        } catch (IOException | RuntimeException e) {
            return Result.retryableFailure("Failed to download DID document of %s: %s".formatted(did, e.getMessage()));
        }
    }

    @Override
    @Nonnull
    public Result<DidDocument> parse(String did, String payload) {
        try {
            return Result.success(mapper.readValue(payload, DidDocument.class));
        } catch (JacksonException e) {
            return Result.failure("DID document of %s is malformed: %s".formatted(did, e.getOriginalMessage()));
        }
    }

    @Override
    @Nonnull
    public Optional<Instant> ownExpiry(DidDocument parsed) {
        return Optional.empty();
    }

    private Result<Download> readBody(String did, Response response) {
        if (!response.isSuccessful()) {
            return Result.failure("Failed to download DID document of %s: HTTP %d".formatted(did, response.code()));
        }
        try {
            var payload = response.body().string();
            return Result.success(new Download(payload, Instant.ofEpochMilli(response.sentRequestAtMillis())));
        } catch (IOException e) {
            return Result.failure("Failed to read the DID document response of %s: %s".formatted(did, e.getMessage()));
        }
    }
}

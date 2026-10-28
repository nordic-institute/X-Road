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
package org.niis.xroad.edc.issuer.didresolution;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.http.spi.FallbackFactory;
import org.eclipse.edc.spi.result.Result;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Delegates {@link #execute(Request)} to the wrapped client and remembers, per calling thread, the status code or
 * the exception of the last request, so that a caller that only receives failure text can tell a non-200 answer
 * from a failed fetch. It supports only the calls that {@code WebDidResolver} makes; every other call throws
 * {@link UnsupportedOperationException}. The fallbacks passed to the calls are ignored, so a failing endpoint
 * is requested once.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class RecordingEdcHttpClient implements EdcHttpClient {

    private final EdcHttpClient delegate;
    private final ThreadLocal<Attempt> lastAttempt = new ThreadLocal<>();

    /**
     * @param status  the HTTP status code, {@code null} when the request failed before a response
     * @param failure the exception of a failed request, {@code null} when a response arrived
     */
    record Attempt(@Nullable Integer status, @Nullable Throwable failure) {
    }

    void forget() {
        lastAttempt.remove();
    }

    @Nullable
    Attempt lastAttempt() {
        return lastAttempt.get();
    }

    @Override
    public Response execute(Request request) throws IOException {
        try {
            var response = delegate.execute(request);
            lastAttempt.set(new Attempt(response.code(), null));
            return response;
        } catch (IOException | RuntimeException e) {
            lastAttempt.set(new Attempt(null, e));
            throw e;
        }
    }

    @Override
    public Response execute(Request request, List<FallbackFactory> fallbacks) throws IOException {
        return execute(request);
    }

    @Override
    public <T> Result<T> execute(Request request, Function<Response, Result<T>> mappingFunction) {
        return execute(request, List.of(), mappingFunction);
    }

    @Override
    public <T> Result<T> execute(Request request, List<FallbackFactory> fallbacks,
                                 Function<Response, Result<T>> mappingFunction) {
        try (var response = execute(request)) {
            return mappingFunction.apply(response);
        } catch (IOException | RuntimeException e) {
            return Result.failure(String.valueOf(e.getMessage()));
        }
    }

    @Override
    public CompletableFuture<Response> executeAsync(Request request, List<FallbackFactory> fallbacks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EdcHttpClient withDns(String dnsServer) {
        throw new UnsupportedOperationException();
    }
}

/*
 * The MIT License
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

package org.niis.xroad.e2e.parity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;

/**
 * Sends a REST call to the consumer Security Server's proxy with the URL exactly as built by
 * {@link RestRequests#url}: the client neither encodes nor normalises the path.
 */
public final class RestSender {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private RestSender() {
    }

    public static Reply send(String method, String url, String clientHeaderValue) {
        return send(method, url, clientHeaderValue, null);
    }

    /**
     * Sends the call with an {@code Accept} header of the given value, or none when it is {@code null}. A
     * {@code null} client header value sends no client header.
     */
    public static Reply send(String method, String url, String clientHeaderValue, String accept) {
        var builder = HttpRequest.newBuilder(URI.create(url))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(REQUEST_TIMEOUT);
        if (clientHeaderValue != null) {
            builder.header(RestRequests.CLIENT_HEADER, clientHeaderValue);
        }
        if (accept != null) {
            builder.header("Accept", accept);
        }
        var request = builder.build();
        try {
            var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            var headers = new LinkedHashMap<String, String>();
            response.headers().map().forEach((name, values) -> headers.put(name, values.get(0)));
            return new Reply(response.statusCode(), headers, response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("call to " + url + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("interrupted while calling " + url, e));
        }
    }
}

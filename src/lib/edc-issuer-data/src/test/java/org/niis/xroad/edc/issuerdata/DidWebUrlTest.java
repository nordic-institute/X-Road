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

import okhttp3.Request;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DidWebUrlTest {

    private final DidWebUrl https = new DidWebUrl(true);

    @Test
    void shouldMapDidWithoutPathToWellKnownLocation() {
        assertThat(https.apply("did:web:ss0.example.org")).isEqualTo("https://ss0.example.org/.well-known/did.json");
    }

    @Test
    void shouldMapEncodedPortToHostPort() {
        assertThat(https.apply("did:web:ss0.example.org%3A7183"))
                .isEqualTo("https://ss0.example.org:7183/.well-known/did.json");
    }

    @Test
    void shouldMapPathSegmentsToDidDocumentUnderThatPath() {
        assertThat(https.apply("did:web:ss0.example.org%3A7183:v1:DEV:COM:222"))
                .isEqualTo("https://ss0.example.org:7183/v1/DEV/COM/222/did.json");
    }

    @Test
    void shouldUsePlainHttpWhenHttpsIsOff() {
        assertThat(new DidWebUrl(false).apply("did:web:ss0.example.org%3A7183"))
                .isEqualTo("http://ss0.example.org:7183/.well-known/did.json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", "urn:web:ss0.example.org",
            "did:web:ss0.example.org:", "did:web:ss0 example.org"})
    void shouldRejectIdentifierThatIsNotValidDidWeb(String did) {
        assertThatThrownBy(() -> https.apply(did)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldMapEveryDidToTheUrlTheStockWebDidResolverRequests(boolean useHttps) {
        var dids = List.of(
                "did:web:ss0.example.org",
                "did:web:ss0.example.org%3A7183",
                "did:web:ss0.example.org:v1:DEV:COM:222",
                "did:web:ss0.example.org%3A7183:v1:DEV:COM:222",
                "did:web:ss0.example.org%3A7183:v1:DEV:COM:222:TESTSERVICE");
        var ours = new DidWebUrl(useHttps);
        for (var did : dids) {
            assertThat(ours.apply(did)).as(did).isEqualTo(urlRequestedByStockResolver(did, useHttps));
        }
    }

    @Test
    void shouldRejectEveryDidTheStockWebDidResolverDoesNotRequest() {
        var dids = List.of(
                "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
                "urn:web:ss0.example.org",
                "did:web:ss0.example.org:",
                "did:web:ss0 example.org");
        var httpClient = mock(EdcHttpClient.class);
        var resolver = new WebDidResolver(httpClient, true, JsonMapper.builder().build(), mock(Monitor.class));
        for (var did : dids) {
            assertThat(resolver.resolve(did).failed()).as(did).isTrue();
            assertThatThrownBy(() -> https.apply(did)).as(did).isInstanceOf(IllegalArgumentException.class);
        }
        verify(httpClient, never()).execute(any(Request.class), anyList(), any());
    }

    private static String urlRequestedByStockResolver(String did, boolean useHttps) {
        var httpClient = mock(EdcHttpClient.class);
        var requests = new ArrayList<Request>();
        when(httpClient.<DidDocument>execute(any(Request.class), anyList(), any())).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return Result.failure("recorded");
        });
        new WebDidResolver(httpClient, useHttps, JsonMapper.builder().build(), mock(Monitor.class)).resolve(did);
        assertThat(requests).as(did).hasSize(1);
        return requests.getFirst().url().toString();
    }
}

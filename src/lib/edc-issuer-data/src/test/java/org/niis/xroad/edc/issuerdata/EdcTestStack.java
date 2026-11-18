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

import dev.failsafe.RetryPolicy;
import okhttp3.OkHttpClient;
import org.eclipse.edc.http.client.EdcHttpClientImpl;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.resolution.DidPublicKeyResolverImpl;
import org.eclipse.edc.iam.did.resolution.DidResolverRegistryImpl;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.did.spi.resolution.DidResolver;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.keys.KeyParserRegistryImpl;
import org.eclipse.edc.keys.keyparsers.JwkParser;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.token.TokenValidationServiceImpl;
import org.eclipse.edc.token.spi.TokenValidationService;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

import static org.mockito.Mockito.mock;

/**
 * The EDC classes that download and verify a status list, wired against plain HTTP DIDs.
 */
record EdcTestStack(ObjectMapper mapper, EdcHttpClient httpClient, TokenValidationService tokenValidationService,
                    DidPublicKeyResolver didPublicKeyResolver) {

    /**
     * Builds the resolver that is registered for {@code web} DIDs from the stock resolver and a fetcher of DID
     * documents over plain HTTP.
     */
    interface DidResolverWrapper {
        DidResolver wrap(DidResolver stockResolver, DidDocumentFetcher fetcher);
    }

    static EdcTestStack create() {
        return create((resolver, _) -> resolver);
    }

    static EdcTestStack create(DidResolverWrapper didResolverWrapper) {
        var monitor = mock(Monitor.class);
        var mapper = new JacksonTypeManager().getMapper();
        var httpClient = new EdcHttpClientImpl(new OkHttpClient(), RetryPolicy.ofDefaults(), monitor);
        var didResolvers = new DidResolverRegistryImpl(Clock.systemUTC(), 0);
        didResolvers.register(didResolverWrapper.wrap(new WebDidResolver(httpClient, false, mapper, monitor),
                plainHttpFetcher(httpClient, mapper)));
        var keyParsers = new KeyParserRegistryImpl();
        keyParsers.register(new JwkParser(mapper, monitor));
        return new EdcTestStack(mapper, httpClient, new TokenValidationServiceImpl(),
                new DidPublicKeyResolverImpl(keyParsers, didResolvers));
    }

    DidDocumentFetcher didDocumentFetcher() {
        return plainHttpFetcher(httpClient, mapper);
    }

    private static DidDocumentFetcher plainHttpFetcher(EdcHttpClient httpClient, ObjectMapper mapper) {
        return new DidDocumentFetcher(httpClient, new DidWebUrl(false), mapper);
    }
}

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

import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Maps a {@code did:web} identifier to the URL of its DID document, as the did:web method specification and the EDC
 * {@code org.eclipse.edc.iam.did.web.resolution.WebDidUrlResolver} do. That class is package-private, so its mapping
 * is repeated here. {@code did:web:ss0.example.org%3A7183} gives
 * {@code https://ss0.example.org:7183/.well-known/did.json}, and {@code did:web:ss0.example.org:v1:DEV:COM:222} gives
 * {@code https://ss0.example.org/v1/DEV/COM/222/did.json}. The mapping throws {@link IllegalArgumentException} for an
 * identifier that is not a valid {@code did:web}.
 */
@RequiredArgsConstructor
final class DidWebUrl implements UnaryOperator<String> {

    private static final String DID_SCHEME = "did";
    private static final String DID_METHOD_PREFIX = "web:";
    private static final String DID_DOCUMENT = "did.json";
    private static final String WELL_KNOWN = "/.well-known";

    private final boolean useHttps;

    @Override
    public String apply(String did) {
        Objects.requireNonNull(did, "did");
        try {
            var location = new URI(scheme() + "://" + hostAndPath(did));
            var url = location.toString();
            if (location.getRawPath().isEmpty()) {
                url += WELL_KNOWN;
            }
            return URLDecoder.decode(url + "/" + DID_DOCUMENT, StandardCharsets.UTF_8);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String scheme() {
        return useHttps ? "https" : "http";
    }

    private static String hostAndPath(String did) throws URISyntaxException {
        var uri = new URI(did);
        if (!DID_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Invalid DID scheme: " + uri.getScheme());
        }
        var part = uri.getRawSchemeSpecificPart();
        if (!part.startsWith(DID_METHOD_PREFIX)) {
            throw new IllegalArgumentException("Invalid DID format, the URN must specify the 'web' DID Method: " + did);
        }
        if (part.endsWith(":")) {
            throw new IllegalArgumentException("Invalid DID format, the URN must not end with ':': " + did);
        }
        return part.substring(DID_METHOD_PREFIX.length()).replace(':', '/');
    }
}

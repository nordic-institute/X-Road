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
package org.niis.xroad.edc.extension.bridge.trust;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * The host and port EDC's own {@code HashicorpVaultExtension} dials, parsed from the same
 * {@code edc.vault.hashicorp.url} setting it reads. Used to scope the vault-CA trust exception
 * strictly to the vault endpoint - scheme default ports are resolved when the URL omits one, and
 * there is deliberately no host-only matching mode.
 */
public record VaultEndpoint(String host, int port) {

    private static final int HTTPS_DEFAULT_PORT = 443;
    private static final int HTTP_DEFAULT_PORT = 80;

    public static Optional<VaultEndpoint> parse(String vaultUrl) {
        if (vaultUrl == null || vaultUrl.isBlank()) {
            return Optional.empty();
        }

        URI uri;
        try {
            uri = new URI(vaultUrl.trim());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }

        var host = uri.getHost();
        var port = resolvePort(uri);
        if (host == null || port <= 0) {
            return Optional.empty();
        }
        return Optional.of(new VaultEndpoint(host, port));
    }

    private static int resolvePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        var scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return HTTPS_DEFAULT_PORT;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return HTTP_DEFAULT_PORT;
        }
        // Scheme absent or not one we know a default port for - cannot resolve, caller must fall
        // back to list-only trust rather than guess.
        return -1;
    }
}

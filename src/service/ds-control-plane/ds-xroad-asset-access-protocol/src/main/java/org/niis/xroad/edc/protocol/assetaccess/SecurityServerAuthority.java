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

package org.niis.xroad.edc.protocol.assetaccess;

import jakarta.annotation.Nonnull;
import okhttp3.HttpUrl;

/**
 * Identifies a provider Security Server by the host and port of a counter-party address, so that every participant
 * context served by one Security Server shares one key, and so that the control plane and the proxy cache agree on
 * which address names which server.
 */
public final class SecurityServerAuthority {

    private SecurityServerAuthority() {
    }

    /**
     * Returns the key of a counter-party address. For an HTTP(S) URL the key is the lowercase host, bracketed when it
     * is an IPv6 literal, then {@code :} and the port, with the scheme's default port when the address names none. For
     * any other address the key is the exact address. Never throws.
     *
     * @param counterPartyAddress the counter-party address
     * @return the key of the address
     */
    @Nonnull
    public static String of(@Nonnull String counterPartyAddress) {
        var url = HttpUrl.parse(counterPartyAddress);
        if (url == null) {
            return counterPartyAddress;
        }
        var host = url.host().indexOf(':') >= 0 ? "[" + url.host() + "]" : url.host();
        return host + ":" + url.port();
    }
}

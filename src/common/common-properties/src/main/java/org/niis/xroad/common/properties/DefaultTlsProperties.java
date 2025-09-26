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

package org.niis.xroad.common.properties;

public final class DefaultTlsProperties {

    private static final String COMMA_SPLIT = "\\s*,\\s*";

    public static final String DEFAULT_PROXY_CLIENT_TLS_PROTOCOLS_STRING = "TLSv1.2";
    public static final String[] DEFAULT_PROXY_CLIENT_TLS_PROTOCOLS = DEFAULT_PROXY_CLIENT_TLS_PROTOCOLS_STRING
            .trim().split(COMMA_SPLIT);

    public static final String DEFAULT_PROXY_CLIENT_SSL_CIPHER_SUITES_STRING = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,"
            + "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,"
            + "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,"
            + "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,"
            + "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384";

    public static final String[] DEFAULT_PROXY_CLIENT_SSL_CIPHER_SUITES = DEFAULT_PROXY_CLIENT_SSL_CIPHER_SUITES_STRING
            .trim().split(COMMA_SPLIT);

    public static final String DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,"
            + "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,"
            + "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384";

    public static final String[] DEFAULT_XROAD_SSL_CIPHER_SUITES = DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING.trim().split(COMMA_SPLIT);

    private DefaultTlsProperties() {
    }

}

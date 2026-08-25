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

/** Helpers for deriving packaged config-key defaults from the deployment environment. */
public final class EnvProperties {

    private static final String XROAD_HOST = "XROAD_HOST";

    private EnvProperties() {
    }

    /**
     * Resolves the {@code XROAD_HOST} environment variable (the service's own hostname),
     * mirroring the legacy {@code ${XROAD_HOST:<default>}} config expression. Used as the packaged
     * default for keys whose value is this host (e.g. RPC TLS certificate common names).
     *
     * @param defaultValue value to use when {@code XROAD_HOST} is unset or blank
     * @return the {@code XROAD_HOST} value, or {@code defaultValue}
     */
    public static String xroadHost(String defaultValue) {
        var value = System.getenv(XROAD_HOST);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

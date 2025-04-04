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
package org.niis.xroad.serverconf.model;

import org.niis.xroad.serverconf.PathGlob;

public interface BaseEndpoint {
    String ANY_METHOD = "*";
    String ANY_PATH = "**";

    String getMethod();

    String getPath();

    String getServiceCode();

    default void validateArguments() {
        if (getServiceCode() == null || getMethod() == null || getPath() == null) {
            throw new IllegalArgumentException("Endpoint parts can not be null");
        }
    }

    default boolean matches(String anotherMethod, String anotherPath) {
        return (ANY_METHOD.equals(getMethod()) || getMethod().equalsIgnoreCase(anotherMethod))
                && (ANY_PATH.equals(getPath()) || PathGlob.matches(getPath(), anotherPath));
    }


    default boolean isEquivalent(BaseEndpoint other) {
        return other.getServiceCode().equals(getServiceCode())
                && other.getMethod().equals(getMethod())
                && other.getPath().equals(getPath());
    }

    /**
     * Return true is this endpoint is base endpoint and false otherwise.
     * Base endpoint is in other words service (code) level endpoint.
     * Each service has one base endpoint.
     * Base endpoint has method '*' and path '**'.
     */
    default boolean isBaseEndpoint() {
        return getMethod().equals(ANY_METHOD) && getPath().equals(ANY_PATH);
    }
}

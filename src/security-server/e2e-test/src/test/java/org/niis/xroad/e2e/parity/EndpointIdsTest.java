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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointIdsTest {

    private static final List<Map<String, Object>> ENDPOINTS = List.of(
            Map.of("id", "11", "method", "*", "path", "/**"),
            Map.of("id", "12", "method", "GET", "path", "/foo"),
            Map.of("id", "13", "method", "GET", "path", "/foo/*"));

    @Test
    void findsTheIdOfTheEndpointWithMatchingMethodAndPath() {
        assertThat(EndpointIds.find(ENDPOINTS, "GET", "/foo")).contains("12");
        assertThat(EndpointIds.find(ENDPOINTS, "GET", "/foo/*")).contains("13");
        assertThat(EndpointIds.find(ENDPOINTS, "*", "/**")).contains("11");
    }

    @Test
    void methodAndPathMustBothMatch() {
        assertThat(EndpointIds.find(ENDPOINTS, "POST", "/foo")).isEmpty();
        assertThat(EndpointIds.find(ENDPOINTS, "GET", "/bar")).isEmpty();
    }

    @Test
    void numericIdIsReturnedAsText() {
        assertThat(EndpointIds.find(List.of(Map.of("id", 7, "method", "GET", "path", "/foo")), "GET", "/foo"))
                .contains("7");
    }

    @Test
    void matchWithoutIdOrMissingListIsEmpty() {
        assertThat(EndpointIds.find(List.of(Map.of("method", "GET", "path", "/foo")), "GET", "/foo")).isEmpty();
        assertThat(EndpointIds.find(null, "GET", "/foo")).isEmpty();
    }
}

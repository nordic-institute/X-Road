/**
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
package org.niis.xroad.centralserver.restapi.util;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

public final class TestUtils {

    private TestUtils() {
        //Utility class
    }

    public static final String API_KEY_HEADER_PREFIX = "X-Road-apikey token=";
    public static final String API_KEY_TOKEN_WITH_ALL_ROLES = "d56e1ca7-4134-4ed4-8030-5f330bdb602a";

    public static void mockServletRequestAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    /**
     * Add Authentication header for API key with all roles
     *
     * @param testRestTemplate
     */
    public static void addApiKeyAuthorizationHeader(TestRestTemplate testRestTemplate) {
        addApiKeyAuthorizationHeader(testRestTemplate, API_KEY_TOKEN_WITH_ALL_ROLES);
    }

    /**
     * Add Authentication header for specific API key
     *
     * @param testRestTemplate
     * @param apiKeyToken      API key token
     */
    public static void addApiKeyAuthorizationHeader(TestRestTemplate testRestTemplate,
            String apiKeyToken) {
        testRestTemplate.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add("Authorization",
                                    API_KEY_HEADER_PREFIX + apiKeyToken);
                    return execution.execute(request, body);
                }));
    }
}

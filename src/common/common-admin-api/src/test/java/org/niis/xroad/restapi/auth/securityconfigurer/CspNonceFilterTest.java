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
package org.niis.xroad.restapi.auth.securityconfigurer;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CspNonceFilter}.
 */
class CspNonceFilterTest {

    private CspNonceFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new CspNonceFilter();
        request = new MockHttpServletRequest("GET", "/");
        response = new MockHttpServletResponse();
    }

    @Test
    void setsNonceAttributeAndCspHeader() throws Exception {
        request.addHeader("Accept", "text/html");

        filter.doFilter(request, response, (req, res) -> { });

        var nonce = (String) request.getAttribute(CspNonceFilter.NONCE_ATTR);
        assertThat(nonce).isNotNull();
        assertThat(response.getHeader("Content-Security-Policy"))
                .isNotNull()
                .contains("nonce-" + nonce);
    }

    @Test
    void nonHtmlRequestStillGetsCspHeader() throws Exception {
        request.addHeader("Accept", "application/json");

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("Content-Security-Policy"))
                .isNotNull()
                .contains("nonce-");
    }

    @Test
    void twoRequestsGetDifferentNonces() throws Exception {
        filter.doFilter(request, response, (req, res) -> { });
        var nonce1 = (String) request.getAttribute(CspNonceFilter.NONCE_ATTR);

        var request2 = new MockHttpServletRequest("GET", "/");
        var response2 = new MockHttpServletResponse();
        filter.doFilter(request2, response2, (req, res) -> { });
        var nonce2 = (String) request2.getAttribute(CspNonceFilter.NONCE_ATTR);

        assertThat(nonce1).isNotEqualTo(nonce2);
    }

    @Test
    void filterDoesNotModifyResponseBody() throws Exception {
        var htmlWithPlaceholder = "<html><script nonce=\"__CSP_NONCE__\">alert(1)</script></html>";

        filter.doFilter(request, response, (req, res) -> {
            var httpRes = (HttpServletResponse) res;
            httpRes.setContentType("text/html");
            httpRes.getOutputStream().write(htmlWithPlaceholder.getBytes(StandardCharsets.UTF_8));
        });

        assertThat(response.getContentAsString()).contains(CspNonceFilter.CSP_NONCE_PLACEHOLDER);
    }
}

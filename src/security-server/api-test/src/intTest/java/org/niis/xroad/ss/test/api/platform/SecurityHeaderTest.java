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
package org.niis.xroad.ss.test.api.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API test for Content Security Policy response headers on the login page.
 */
@DisplayName("Security headers — CSP nonce on login page")
@SuppressWarnings("checkstyle:magicnumber")
class SecurityHeaderTest extends SsApiTest {

    private static final Pattern NONCE_PATTERN = Pattern.compile("'nonce-([A-Za-z0-9+/=]+)'");

    // MIGRATED-FROM: 0230-ss-security-header.feature :: "Verify that content security headers are set correctly"
    @Test
    @DisplayName("Login page response contains a Content-Security-Policy header with a well-formed nonce")
    void loginPageResponseHasValidCspNonce(SsApiTestContainerSetup stack) {
        var mapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var baseUrl = "https://%s:%d".formatted(mapping.host(), mapping.port());

        var cspHeader = when("a GET request is made to the login page", () ->
                RestAssuredFactory.given()
                        .accept("text/html")
                        .get(baseUrl)
                        .then()
                        .statusCode(200)
                        .extract()
                        .header("Content-Security-Policy"));

        then("the Content-Security-Policy header is present", () ->
                assertThat(cspHeader).isNotNull().isNotBlank());

        then("the CSP header contains a nonce directive with a base64-encoded nonce (32 bytes = 44 chars)", () -> {
            var matcher = NONCE_PATTERN.matcher(cspHeader);
            assertThat(matcher.find())
                    .as("CSP header should contain a nonce directive matching 'nonce-<base64>'")
                    .isTrue();
            var nonce = matcher.group(1);
            assertThat(nonce)
                    .as("nonce should be 44 characters (32 bytes base64-encoded)")
                    .hasSize(44);
        });
    }
}

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
package org.niis.xroad.cs.test.api.security;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

@SuppressWarnings("checkstyle:magicnumber")
class SecurityHeaderTest extends CsApiTest {

    private static final int CSP_NONCE_BASE64_LENGTH = 44; // 32 bytes encoded in base64

    @Test
    void cspNoncePresentInHeaderAndAllTagsInLoginPage(CsBaselineSeeder seeder) {
        var baseUrl = seeder.getAdminBaseUrl();

        then("GET / returns HTML with a CSP nonce header and every script/link/style tag carries that nonce", () -> {
            Response response = RestAssuredFactory.given()
                    .baseUri(baseUrl)
                    .accept("text/html")
                    .get("/")
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();

            String cspHeader = response.getHeader("Content-Security-Policy");
            assertThat(cspHeader).as("Content-Security-Policy header must be present").isNotNull();

            String nonce = extractNonceFromCspHeader(cspHeader);
            assertThat(nonce).as("CSP header must contain a nonce- directive").isNotNull();
            assertThat(nonce).as("nonce value must be 44 characters (32 bytes base64)").hasSize(CSP_NONCE_BASE64_LENGTH);

            String html = response.getBody().asString();
            verifyNoncesForElement(html, "script", nonce);
            verifyNoncesForElement(html, "link", nonce);
            verifyNoncesForElement(html, "style", nonce);
        });
    }

    private static String extractNonceFromCspHeader(String cspHeader) {
        Matcher matcher = Pattern.compile("nonce-([A-Za-z0-9+/=]+)").matcher(cspHeader);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void verifyNoncesForElement(String html, String elementName, String expectedNonce) {
        int totalTags = countElementTags(html, elementName);
        List<String> nonces = extractNoncesFromHtml(html, elementName);
        if (totalTags > 0) {
            assertThat(nonces.size())
                    .as("every <%s> tag must carry a nonce attribute", elementName)
                    .isEqualTo(totalTags);
            for (String nonce : nonces) {
                assertThat(nonce)
                        .as("nonce on <%s> tag must match the CSP header nonce", elementName)
                        .isEqualTo(expectedNonce);
            }
        }
    }

    private static List<String> extractNoncesFromHtml(String html, String elementName) {
        List<String> nonces = new ArrayList<>();
        Pattern pattern = Pattern.compile(
                "<" + elementName + "[^>]*nonce=[\"']?([^\"' >]+)[\"']?[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            nonces.add(matcher.group(1));
        }
        return nonces;
    }

    private static int countElementTags(String html, String elementName) {
        String regex = "link".equals(elementName)
                ? "<" + elementName + "[^>]*rel=[\"']?(stylesheet|modulepreload)[\"']?[^>]*>"
                : "<" + elementName + "[^>]*>";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}

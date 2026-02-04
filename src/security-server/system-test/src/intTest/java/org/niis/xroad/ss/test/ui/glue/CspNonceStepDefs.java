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
package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.WebDriverRunner;
import io.cucumber.java.en.Then;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class CspNonceStepDefs extends BaseUiStepDefs {

    @SuppressWarnings("checkstyle:MagicNumber")
    @Then("the response should have a valid CSP nonce in the header and tags")
    public void pageShouldHaveValidCspNonce() throws Exception {
        String pageUrl = WebDriverRunner.getWebDriver().getCurrentUrl();
        URL url = URI.create(pageUrl).toURL();
        HttpsURLConnection conn = getHttpsURLConnection(url);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "text/html");
        conn.connect();
        String html = getHtmlResponse(conn);
        String nonce = getCspNonceFromHeader(conn);

        assertThat(nonce).isNotNull();
        assertThat(nonce).hasSize(44); // 32 bytes in base64 is 44 characters
        verifyNoncesForElement(html, "script", nonce);
        verifyNoncesForElement(html, "link", nonce);
        verifyNoncesForElement(html, "style", nonce);
    }

    private static String getHtmlResponse(HttpsURLConnection conn) throws IOException {
        StringBuilder htmlBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                htmlBuilder.append(line).append("\n");
            }
        }
        return htmlBuilder.toString();
    }

    private static HttpsURLConnection getHttpsURLConnection(URL url) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        conn.setSSLSocketFactory(sslContext.getSocketFactory());
        conn.setHostnameVerifier((hostname, session) -> true);
        return conn;
    }

    private static String getCspNonceFromHeader(HttpsURLConnection conn) {
        String cspHeader = conn.getHeaderField("Content-Security-Policy");
        if (cspHeader == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("nonce-([A-Za-z0-9+/=]+)").matcher(cspHeader);
        return matcher.find() ? matcher.group(1) : null;
    }


    private void verifyNoncesForElement(String html, String elementName, String expectedNonce) {
        int totalTags = countElementTags(html, elementName);
        List<String> nonces = extractNoncesFromHtml(html, elementName);
        if (totalTags > 0) {
            assertThat(nonces.size()).isEqualTo(totalTags);
            for (String nonce : nonces) {
                assertThat(nonce).isEqualTo(expectedNonce);
            }
        }
    }

    public static List<String> extractNoncesFromHtml(String html, String elementName) {
        List<String> nonces = new ArrayList<>();
        Pattern pattern = Pattern.compile("<" + elementName + "[^>]*nonce=[\"']?([^\"' >]+)[\"']?[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            nonces.add(matcher.group(1));
        }
        return nonces;
    }

    private int countElementTags(String html, String elementName) {
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

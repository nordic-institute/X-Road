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

package org.niis.xroad.ss.test.ds.glue;

import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.ss.test.SsSystemTestContainerSetup;
import org.niis.xroad.ss.test.addons.glue.BaseStepDefs;
import org.niis.xroad.ss.test.ui.container.Port;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SuppressWarnings({"checkstyle:MagicNumber", "SpringJavaInjectionPointsAutowiringInspection"})
public class DataspaceProvisioningStepDefs extends BaseStepDefs {

    private static final String STATUS_ISSUED = "ISSUED";
    private static final int MAX_ATTEMPTS = 30;
    private static final long POLL_INTERVAL_MILLIS = 3000L;

    private final CookieManager cookieManager = new CookieManager();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SsSystemTestContainerSetup containerSetup;

    private String provisioningStatus;

    @SneakyThrows
    @SuppressWarnings("checkstyle:SneakyThrowsCheck")
    @Step("Data space provisioning is requested on the security server")
    public void dataSpaceProvisioningIsRequested() {
        var mapping = containerSetup.getContainerMapping(SsSystemTestContainerSetup.UI, Port.UI);
        var baseUrl = "https://" + mapping.host() + ":" + mapping.port();
        var httpClient = buildTrustAllHttpClient();

        login(httpClient, baseUrl);
        var xsrfToken = readXsrfToken();

        var provisioningUri = URI.create(baseUrl + "/api/v1/system/dataspace-provisioning");
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            var request = HttpRequest.newBuilder(provisioningUri)
                    .header("X-XSRF-TOKEN", xsrfToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            provisioningStatus = extractStatus(response);
            log.info("Data space provisioning attempt {}/{}: HTTP {}, status {}",
                    attempt, MAX_ATTEMPTS, response.statusCode(), provisioningStatus);
            if (STATUS_ISSUED.equals(provisioningStatus)) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
    }

    @Step("Data space provisioning status is {string}")
    public void dataSpaceProvisioningStatusIs(String expectedStatus) {
        assertEquals(expectedStatus, provisioningStatus);
    }

    private void login(HttpClient httpClient, String baseUrl) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=xrd&password=secret123!"))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Security server login responded with HTTP {}", response.statusCode());
    }

    private String readXsrfToken() {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(java.net.HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("XSRF-TOKEN cookie not present after login"));
    }

    private String extractStatus(HttpResponse<String> response) {
        var body = response.body();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(body);
            var status = node.get("status");
            return status != null ? status.asString() : null;
        } catch (Exception e) {
            log.warn("Failed to parse provisioning response body: {}", body, e);
            return null;
        }
    }

    @SneakyThrows
    @SuppressWarnings("checkstyle:SneakyThrowsCheck")
    private HttpClient buildTrustAllHttpClient() {
        var trustAll = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{trustAll}, null);
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}

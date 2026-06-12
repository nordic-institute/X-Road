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
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.niis.xroad.ss.test.SsSystemTestContainerSetup;
import org.niis.xroad.ss.test.addons.glue.BaseStepDefs;
import org.niis.xroad.ss.test.ui.container.Port;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SuppressWarnings({"checkstyle:MagicNumber", "SpringJavaInjectionPointsAutowiringInspection"})
public class DataspaceProvisioningStepDefs extends BaseStepDefs {

    private static final String STATUS_ISSUED = "ISSUED";
    private static final int MAX_ATTEMPTS = 30;
    private static final long POLL_INTERVAL_MILLIS = 3000L;

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
        var cookieStore = new BasicCookieStore();

        try (var httpClient = buildTrustAllHttpClient(cookieStore)) {
            login(httpClient, baseUrl);
            var xsrfToken = readXsrfToken(cookieStore);

            var provisioningUrl = baseUrl + "/api/v1/system/dataspace-provisioning";
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                var request = new HttpPost(provisioningUrl);
                request.addHeader("X-XSRF-TOKEN", xsrfToken);
                var response = httpClient.execute(request, this::toResponse);
                provisioningStatus = extractStatus(response.body());
                log.info("Data space provisioning attempt {}/{}: HTTP {}, status {}",
                        attempt, MAX_ATTEMPTS, response.status(), provisioningStatus);
                if (STATUS_ISSUED.equals(provisioningStatus)) {
                    return;
                }
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }
        }
    }

    @Step("Data space provisioning status is {string}")
    public void dataSpaceProvisioningStatusIs(String expectedStatus) {
        assertEquals(expectedStatus, provisioningStatus);
    }

    private void login(CloseableHttpClient httpClient, String baseUrl) throws IOException {
        var request = new HttpPost(baseUrl + "/login");
        request.setEntity(new UrlEncodedFormEntity(List.of(
                new BasicNameValuePair("username", "xrd"),
                new BasicNameValuePair("password", "secret123!"))));
        var response = httpClient.execute(request, this::toResponse);
        log.info("Security server login responded with HTTP {}", response.status());
    }

    private String readXsrfToken(BasicCookieStore cookieStore) {
        return cookieStore.getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("XSRF-TOKEN cookie not present after login"));
    }

    private HttpResult toResponse(ClassicHttpResponse response) throws IOException, ParseException {
        var entity = response.getEntity();
        var body = entity != null ? EntityUtils.toString(entity) : null;
        return new HttpResult(response.getCode(), body);
    }

    private String extractStatus(String body) {
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
    private CloseableHttpClient buildTrustAllHttpClient(BasicCookieStore cookieStore) {
        var sslContext = SSLContexts.custom()
                .loadTrustMaterial((chain, authType) -> true)
                .build();
        var connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))
                .build();
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(
                        new DefaultClientTlsStrategy(sslContext, HostnameVerificationPolicy.CLIENT, NoopHostnameVerifier.INSTANCE))
                .setDefaultConnectionConfig(connectionConfig)
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultCookieStore(cookieStore)
                .build();
    }

    private record HttpResult(int status, String body) {
    }
}

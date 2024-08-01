/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.edc.management.client.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Client;
import feign.Feign;
import feign.hc5.ApacheHttp5Client;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs3.JAXRS3Contract;
import jakarta.ws.rs.core.MediaType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.eclipse.edc.connector.dataplane.selector.control.api.DataplaneSelectorControlApi;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.niis.xroad.edc.management.client.FeignDataplaneSelectorControlApi;
import org.niis.xroad.ssl.SSLContextBuilder;

@Slf4j
public class EdcControlApiFactory {

    private final ObjectMapper objectMapper = JacksonJsonLd.createObjectMapper();
    private final Client client = apacheHttpClient(feignClient());
    private final String baseUrl;

    public EdcControlApiFactory(String baseUrl) {
        this.baseUrl = baseUrl;

    }

    public DataplaneSelectorControlApi dataplaneSelectorControlApi() {
        return createFeignClient(FeignDataplaneSelectorControlApi.class, "/v1/dataplanes");
    }

    private <T> T createFeignClient(Class<T> apiClass, String path) {
        return Feign.builder().client(client)
                .contract(new JAXRS3Contract())
                .encoder(new JacksonEncoder(objectMapper))
                .decoder(new JacksonDecoder(objectMapper))
                .requestInterceptor(requestTemplate -> {
                    requestTemplate.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                    if (requestTemplate.body() != null) {
                        log.info("Request: [{}] Body: [{}]", baseUrl + requestTemplate.request().url(),
                                new String(requestTemplate.request().body()));
                    }
                })

                .target(apiClass, baseUrl + "/control" + path);
    }

    public Client apacheHttpClient(final CloseableHttpClient httpClient) {
        return new ApacheHttp5Client(httpClient);
    }

    public CloseableHttpClient feignClient() {
        return HttpClients.custom()
                .setConnectionManager(buildConnectionManager())
                .setDefaultRequestConfig(RequestConfig.DEFAULT)
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableRedirectHandling()
                .build();
    }

    @SneakyThrows
    private HttpClientConnectionManager buildConnectionManager() {
        final var sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setHostnameVerifier(new NoopHostnameVerifier())
                .setSslContext(SSLContextBuilder.create().sslContext())
                .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
    }

}

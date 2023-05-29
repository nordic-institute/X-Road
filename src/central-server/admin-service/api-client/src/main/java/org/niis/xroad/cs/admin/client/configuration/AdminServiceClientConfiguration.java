/**
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
package org.niis.xroad.cs.admin.client.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.hc5.ApacheHttp5Client;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.niis.xroad.cs.admin.client.AuthFeignClientInterceptor;
import org.niis.xroad.cs.admin.client.FeignManagementRequestsApi;
import org.niis.xroad.cs.admin.client.FeignRestErrorDecoder;
import org.niis.xroad.cs.admin.client.XForwardedForHeaderFeignInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import static org.apache.hc.core5.util.Timeout.ofSeconds;


@Import(FeignClientsConfiguration.class)
@Configuration
public class AdminServiceClientConfiguration {

    @Bean
    public ErrorDecoder errorDecoder(final ObjectMapper objectMapper) {
        return new FeignRestErrorDecoder(objectMapper);
    }

    @Bean
    public FeignManagementRequestsApi feignManagementRequestsApi(Decoder decoder,
                                                                 @Qualifier("adminServiceFeignClient") Client client,
                                                                 Encoder encoder,
                                                                 ErrorDecoder errorDecoder,
                                                                 Contract contract,
                                                                 AdminServiceClientPropertyProvider propertyProvider) {
        return Feign.builder().client(client)
                .encoder(encoder)
                .decoder(decoder)
                .errorDecoder(errorDecoder)
                .requestInterceptor(new AuthFeignClientInterceptor(propertyProvider))
                .requestInterceptor(new XForwardedForHeaderFeignInterceptor())
                .contract(contract)
                .target(FeignManagementRequestsApi.class, propertyProvider.getApiBaseUrl().toString());
    }

    @Bean("adminServiceFeignClient")
    public Client apacheHttpClient(@Qualifier("adminServiceHttpClient") final CloseableHttpClient httpClient) {
        return new ApacheHttp5Client(httpClient);
    }

    @Bean("adminServiceHttpClient")
    public CloseableHttpClient feignClient(final AdminServiceClientPropertyProvider propertyProvider)
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, CertificateException, IOException {
        var httpClientProperties = propertyProvider.getHttpClientProperties();
        return HttpClients.custom()
                .setConnectionManager(buildConnectionManager(propertyProvider))
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(ofSeconds(httpClientProperties.getConnectionTimeoutSeconds()))
                        .setResponseTimeout(ofSeconds(httpClientProperties.getResponseTimeoutSeconds()))
                        .setConnectionRequestTimeout(ofSeconds(httpClientProperties.getConnectionRequestTimeoutSeconds()))
                        .build())
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableRedirectHandling()
                .setUserAgent(propertyProvider.getUserAgent())
                .build();
    }

    private HttpClientConnectionManager buildConnectionManager(final AdminServiceClientPropertyProvider propertyProvider)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException {
        final var sslcontext = SSLContexts.custom()
                .setProtocol("TLSv1.3")
                .loadTrustMaterial(
                        propertyProvider.getApiTrustStore().toFile(),
                        propertyProvider.getApiTrustStorePassword().toCharArray())
                .build();

        final var sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setHostnameVerifier(new NoopHostnameVerifier())
                .setSslContext(sslcontext)
                .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(propertyProvider.getHttpClientProperties().getMaxConnectionsPerRoute())
                .setMaxConnTotal(propertyProvider.getHttpClientProperties().getMaxConnectionsTotal())
                .setSSLSocketFactory(sslSocketFactory)
                .build();
    }
}

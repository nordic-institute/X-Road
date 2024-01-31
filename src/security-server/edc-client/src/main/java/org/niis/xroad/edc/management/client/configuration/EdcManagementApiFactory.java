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
import feign.jaxrs.JakartaContract;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.ssl.SSLContexts;
import org.eclipse.edc.connector.api.management.asset.v3.AssetApi;
import org.eclipse.edc.connector.api.management.contractdefinition.ContractDefinitionApi;
import org.eclipse.edc.connector.api.management.contractnegotiation.ContractNegotiationApi;
import org.eclipse.edc.connector.api.management.policy.PolicyDefinitionApi;
import org.eclipse.edc.connector.api.management.transferprocess.TransferProcessApi;
import org.eclipse.edc.connector.dataplane.selector.api.v2.DataplaneSelectorApi;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.niis.xroad.edc.management.client.FeignAssetApi;
import org.niis.xroad.edc.management.client.FeignCatalogApi;
import org.niis.xroad.edc.management.client.FeignContractDefinitionApi;
import org.niis.xroad.edc.management.client.FeignContractNegotiationApi;
import org.niis.xroad.edc.management.client.FeignPolicyDefinitionApi;
import org.niis.xroad.edc.management.client.FeignTransferProcessApi;

@Slf4j
public class EdcManagementApiFactory {
    private final ObjectMapper objectMapper = JacksonJsonLd.createObjectMapper();
    private final Client client = apacheHttpClient(feignClient());
    private final String baseUrl;

    public EdcManagementApiFactory(String baseUrl) {
        this.baseUrl = baseUrl;

    }

    public DataplaneSelectorApi dataplaneSelectorApi() {
        return createFeignClient(DataplaneSelectorApi.class, "/v2/dataplanes");
    }

    public AssetApi assetsApi() {
        return createFeignClient(FeignAssetApi.class, "/v3/assets");
    }

    public FeignCatalogApi catalogApi() {
        return createFeignClient(FeignCatalogApi.class, "/v2/catalog");
    }

    public PolicyDefinitionApi policyDefinitionApi() {
        return createFeignClient(FeignPolicyDefinitionApi.class, "/v2/policydefinitions");
    }

    public ContractDefinitionApi contractDefinitionApi() {
        return createFeignClient(FeignContractDefinitionApi.class, "/v2/contractdefinitions");
    }

    public ContractNegotiationApi contractNegotiationApi() {
        return createFeignClient(FeignContractNegotiationApi.class, "/v2/contractnegotiations");
    }

    public TransferProcessApi transferProcessApi() {
        return createFeignClient(FeignTransferProcessApi.class, "/v2/transferprocesses");
    }

    private <T> T createFeignClient(Class<T> apiClass, String path) {
        return Feign.builder().client(client)
                .contract(new JakartaContract())
                .encoder(new JacksonEncoder(objectMapper))
                .decoder(new JacksonDecoder(objectMapper))
                .requestInterceptor(requestTemplate -> {
                    requestTemplate.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                    if (requestTemplate.body() != null) {
                        log.info("Request: [{}] Body: [{}]", baseUrl + requestTemplate.request().url(),
                                new String(requestTemplate.request().body()));
                    }
                })

                .target(apiClass, baseUrl + "/management" + path);
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

    private HttpClientConnectionManager buildConnectionManager() {
        final var sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setHostnameVerifier(new NoopHostnameVerifier())
                .setSslContext(SSLContexts.createDefault())
                .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
    }
}

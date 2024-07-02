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

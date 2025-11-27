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
package org.niis.xroad.ss.test.addons;

import ee.ria.xroad.common.util.MimeTypes;

import feign.Contract;
import feign.Feign;
import feign.Logger;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.hc5.ApacheHttp5Client;
import feign.jackson.JacksonEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.niis.xroad.ss.test.SsSystemTestContainerSetup;
import org.niis.xroad.ss.test.addons.api.FeignHealthcheckApi;
import org.niis.xroad.ss.test.addons.api.FeignTestCaApi;
import org.niis.xroad.ss.test.addons.api.FeignXRoadRestRequestsApi;
import org.niis.xroad.ss.test.addons.api.FeignXRoadSoapRequestsApi;
import org.niis.xroad.ss.test.ui.container.Port;
import org.niis.xroad.test.framework.core.feign.FeignFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.PROXY;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SsAddonTestConfiguration {
    private final FeignFactory feignFactory;
    private final SsSystemTestContainerSetup systemTestContainerSetup;

    private String getProxyBaseUrl() {
        var container = systemTestContainerSetup.getContainerMapping(PROXY, Port.PROXY_HTTP);

        return String.format("http://%s:%s".formatted(
                container.host(),
                container.port()
        ));
    }

    @Bean
    public FeignXRoadSoapRequestsApi feignManagementRequestsApi(
            Decoder decoder,
            Contract contract) {
        return Feign.builder()
                .logLevel(Logger.Level.FULL)
                .client(new ApacheHttp5Client(HttpClients.createDefault()))
                .encoder(new Encoder.Default())
                .decoder(decoder)
                .requestInterceptor(requestTemplate -> requestTemplate
                        .target(getProxyBaseUrl())
                        .header("Content-Type", MimeTypes.TEXT_XML_UTF8)
                        .header("x-hash-algorithm", "SHA-512")
                )
                .contract(contract)
                .target(FeignXRoadSoapRequestsApi.class, "http://localhost");
    }

    @Bean
    public FeignXRoadRestRequestsApi feignXRoadRestRequestsApi(Decoder decoder,
                                                               Contract contract) {
        return Feign.builder()
                .logLevel(Logger.Level.FULL)
                .client(new ApacheHttp5Client(HttpClients.createDefault()))
                .encoder(new JacksonEncoder())
                .decoder(decoder)
                .requestInterceptor(requestTemplate -> {
                    requestTemplate.target(getProxyBaseUrl());
                    requestTemplate.header("Content-Type", MimeTypes.JSON);
                })
                .contract(contract)
                .target(FeignXRoadRestRequestsApi.class, "http://localhost");
    }

    @Bean
    @SuppressWarnings("checkstyle:MagicNumber")
    public FeignHealthcheckApi feignHealthcheckApi(
            Decoder decoder,
            Contract contract) {
        return Feign.builder()
                .logLevel(Logger.Level.FULL)
                .client(new ApacheHttp5Client(HttpClients.createDefault()))
                .encoder(new Encoder.Default())
                .decoder(decoder)
                .requestInterceptor(requestTemplate -> requestTemplate
                        .target("http://%s:%d".formatted(
                                systemTestContainerSetup.getContainerMapping(PROXY, Port.PROXY_HEALTHCHECK).host(),
                                systemTestContainerSetup.getContainerMapping(PROXY, Port.PROXY_HEALTHCHECK).port())))
                .contract(contract)
                .target(FeignHealthcheckApi.class, "http://localhost");
    }

    @Bean
    public FeignTestCaApi testCaFeignApi() {
        var caContainer = systemTestContainerSetup.getContainerMapping(SsSystemTestContainerSetup.TESTCA, Port.TEST_CA);
        return feignFactory.createClient(FeignTestCaApi.class, "http://%s:%d/testca".formatted(
                caContainer.host(),
                caContainer.port()));
    }
}

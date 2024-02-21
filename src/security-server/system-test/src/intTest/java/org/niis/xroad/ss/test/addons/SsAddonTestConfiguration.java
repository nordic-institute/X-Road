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

import com.nortal.test.core.services.TestableApplicationInfoProvider;
import feign.Contract;
import feign.Feign;
import feign.Logger;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.hc5.ApacheHttp5Client;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.niis.xroad.ss.test.addons.api.FeignHealthcheckApi;
import org.niis.xroad.ss.test.addons.api.FeignXRoadSoapRequestsApi;
import org.niis.xroad.ss.test.addons.jmx.JmxClient;
import org.niis.xroad.ss.test.addons.jmx.JmxClientImpl;
import org.niis.xroad.ss.test.ui.container.Port;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.niis.xroad.ss.test.ui.container.ContainerSetup.JMX_PORT_SUPPLIER;
import static org.niis.xroad.ss.test.ui.container.Port.HEALTHCHECK;

@Slf4j
@Configuration
@Import(FeignClientsConfiguration.class)
public class SsAddonTestConfiguration {

    private static final String JMX_URL_TEMPLATE = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";

    @Bean
    public FeignXRoadSoapRequestsApi feignManagementRequestsApi(
            Decoder decoder, TestableApplicationInfoProvider appInfoProvider, Contract contract) {
        return Feign.builder()
                .logLevel(Logger.Level.FULL)
                .client(new ApacheHttp5Client(HttpClients.createDefault()))
                .encoder(new Encoder.Default())
                .decoder(decoder)
                .requestInterceptor(requestTemplate -> requestTemplate
                        .target(String.format("http://%s:%s", appInfoProvider.getHost(), appInfoProvider.getMappedPort(Port.SERVICE)))
                        .header("Content-Type", MimeTypes.TEXT_XML_UTF8)
                        .header("x-hash-algorithm", "SHA-512")
                )
                .contract(contract)
                .target(FeignXRoadSoapRequestsApi.class, "http://localhost");
    }

    @Bean
    @SuppressWarnings("checkstyle:MagicNumber")
    public FeignHealthcheckApi feignHealthcheckApi(
            Decoder decoder, TestableApplicationInfoProvider appInfoProvider, Contract contract) {
        return Feign.builder()
                .logLevel(Logger.Level.FULL)
                .client(new ApacheHttp5Client(HttpClients.createDefault()))
                .encoder(new Encoder.Default())
                .decoder(decoder)
                .requestInterceptor(requestTemplate -> requestTemplate
                        .target(String.format("http://%s:%s", appInfoProvider.getHost(), appInfoProvider.getMappedPort(HEALTHCHECK))))
                .contract(contract)
                .target(FeignHealthcheckApi.class, "http://localhost");
    }

    @Bean
    public JmxClient jmxClient(TestableApplicationInfoProvider appInfoProvider) {
        return new JmxClientImpl(() -> String.format(JMX_URL_TEMPLATE, appInfoProvider.getHost(), JMX_PORT_SUPPLIER.get()));
    }

}

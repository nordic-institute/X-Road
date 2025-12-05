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
package org.niis.xroad.test.framework.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Client;
import feign.Contract;
import feign.Logger;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import feign.hc5.ApacheHttp5Client;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.niis.xroad.test.framework.core.feign.ResourceAwareDecoder;
import org.niis.xroad.test.framework.core.report.ResourceSerializingModule;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Configuration
@ComponentScan("org.niis.xroad.test.framework.core")
@Import(DynamicPackageScanImportSelector.class)
public class TestFrameworkCoreConfig {

    @Bean
    TestFrameworkConfigSource testFrameworkConfigSource() {
        return TestFrameworkConfigSource.getInstance();
    }

    @Bean
    public TestFrameworkCoreProperties testAutomationCoreProperties() {
        return TestFrameworkConfigSource.getInstance().getCoreProperties();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModules(new JavaTimeModule(), new ResourceSerializingModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Bean
    Contract feignContract() {
        return new SpringMvcContract();
    }

    @Bean
    Client feignClient(TestFrameworkCoreProperties properties) {
        var connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(properties.feign().connectTimeout()))
                .build();

        var socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(properties.feign().readTimeout()))
                .build();

        var connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        connectionManager.setDefaultSocketConfig(socketConfig);

        var httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        return new ApacheHttp5Client(httpClient);
    }

    @Bean
    Encoder feignEncoder(ObjectMapper objectMapper) {
        var jacksonEncoder = new JacksonEncoder(objectMapper);
        return new SpringFormEncoder(jacksonEncoder);
    }

    @Bean
    Decoder feignDecoder(ObjectMapper objectMapper) {
        var jacksonDecoder = new JacksonDecoder(objectMapper);
        var responseEntityDecoder = new ResponseEntityDecoder(jacksonDecoder);
        return new ResourceAwareDecoder(responseEntityDecoder);
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

}

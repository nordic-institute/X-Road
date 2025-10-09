/*
 * The MIT License
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
package org.niis.xroad.ss.test.api.configuration;

import feign.Contract;
import feign.Feign;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.hc5.ApacheHttp5Client;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.niis.xroad.ss.test.api.FeignBackupsApi;
import org.niis.xroad.ss.test.api.FeignInitializationApi;
import org.niis.xroad.ss.test.ui.container.EnvSetup;
import org.niis.xroad.ss.test.ui.container.Port;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.cert.X509Certificate;

@Configuration
@SuppressWarnings("checkstyle:MagicNumber")
public class ApiTestConfiguration {

    @Bean
    public FeignBackupsApi feignBackupsApi(
            @Qualifier("uiHostInterceptor") RequestInterceptor uiHostInterceptor,
            Decoder decoder,
            EnvSetup envSetup,
            Contract contract) {
        return Feign.builder()
                .logLevel(Logger.Level.FULL)
                .client(insecureClient())
                .encoder(new Encoder.Default())
                .decoder(decoder)
                .requestInterceptor(uiHostInterceptor)
                .contract(contract)
                .target(FeignBackupsApi.class, "http://localhost");
    }

    @Bean
    public FeignInitializationApi feignInitializationApi(
            @Qualifier("uiHostInterceptor") RequestInterceptor uiHostInterceptor,
            Decoder decoder,
            EnvSetup envSetup,
            Contract contract) {
        return Feign.builder()
                .logLevel(Logger.Level.FULL)
                .client(insecureClient())
                .encoder(new Encoder.Default())
                .decoder(decoder)
                .requestInterceptor(uiHostInterceptor)
                .contract(contract)
                .target(FeignInitializationApi.class, "http://localhost");
    }

    @Bean("uiHostInterceptor")
    public RequestInterceptor uiHostInterceptor(EnvSetup envSetup) {
        return requestTemplate -> requestTemplate
                .target("https://%s:%d/api".formatted(
                        envSetup.getContainerMapping(EnvSetup.UI, Port.UI).host(),
                        envSetup.getContainerMapping(EnvSetup.UI, Port.UI).port()));
    }

    private static ApacheHttp5Client insecureClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);

            return new ApacheHttp5Client(HttpClients.custom()
                    .setConnectionManager(org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                            .setTlsSocketStrategy(new DefaultClientTlsStrategy(sslContext, NoopHostnameVerifier.INSTANCE))
                            .build())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

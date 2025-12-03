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
package org.niis.xroad.test.framework.core.feign;

import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FeignFactory {
    private final Contract contract;
    private final Client client;
    private final Encoder defaultEncoder;
    private final Decoder defaultDecoder;
    private final Logger.Level loggerLevel;
    private final List<RequestInterceptor> requestInterceptors;
    private final FeignReportLogger feignReportLogger;

    public <T> T createClient(Class<T> clientClass, String baseUrl) {
        return createClient(clientClass, baseUrl, true);
    }

    public <T> T createClient(Class<T> clientClass, String baseUrl, boolean registerDefaultInterceptors) {
        return createClient(clientClass, baseUrl, defaultEncoder,
                registerDefaultInterceptors ? requestInterceptors : Collections.emptyList());
    }

    public <T> T createClient(Class<T> clientClass, String baseUrl, Encoder encoder, List<RequestInterceptor> interceptors) {
        Feign.Builder builder = Feign.builder()
                .contract(contract)
                .client(client)
                .encoder(encoder)
                .decoder(defaultDecoder)
                .logger(feignReportLogger)
                .logLevel(loggerLevel);

        // Add all interceptors
        for (RequestInterceptor interceptor : interceptors) {
            builder.requestInterceptor(interceptor);
        }

        return builder.target(clientClass, baseUrl);
    }
}

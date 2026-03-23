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

package org.niis.xroad.ss.test.ds.api;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.ss.test.SsSystemTestContainerSetup;
import org.niis.xroad.ss.test.ui.container.Port;
import org.niis.xroad.test.framework.core.feign.FeignFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Type;
import java.util.Collections;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.DS_CONTROL_PLANE;

@Configuration
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class FeignClientConfiguration {

    private final FeignFactory feignFactory;
    private final SsSystemTestContainerSetup systemTestContainerSetup;

    private String getBaseUrl() {
        var container = systemTestContainerSetup.getContainerMapping(DS_CONTROL_PLANE, Port.DS_CONTROL_PLANE_MANAGEMENT);
        return "http://%s:%d/api/mgmt/v4alpha/participants".formatted(container.host(), container.port());
    }

    @Bean
    FeignControlPlaneManagementApi feignControlPlaneManagementApi(Encoder defaultEncoder) {
        var rawStringEncoder = new RawStringEncoder(defaultEncoder);
        return feignFactory.createClient(FeignControlPlaneManagementApi.class, getBaseUrl(),
                rawStringEncoder, Collections.emptyList());
    }

    @RequiredArgsConstructor
    static class RawStringEncoder implements Encoder {
        private final Encoder delegate;

        @Override
        public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
            if (object instanceof String body) {
                // Send String as raw bytes without JSON encoding
                template.body(body.getBytes(UTF_8), UTF_8);
            } else {
                // For all other types, use the default JSON encoder
                delegate.encode(object, bodyType, template);
            }
        }
    }
}

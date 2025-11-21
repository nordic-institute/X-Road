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
package org.niis.xroad.cs.test.api.configuration;

import com.nortal.test.feign.interceptor.FeignClientInterceptor;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.niis.xroad.cs.test.container.CsAdminServiceIntTestSetup;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class HostFeignClientInterceptor implements FeignClientInterceptor {
    private static final int EXECUTION_ORDER = 10;

    private final CsAdminServiceIntTestSetup intTestSetup;

    @Override
    public int getOrder() {
        return EXECUTION_ORDER;
    }

    @NotNull
    @Override
    public Response intercept(@Nonnull Interceptor.Chain chain) throws IOException {
        var request = chain.request();
        var csContainer = intTestSetup.getContainerMapping(CsAdminServiceIntTestSetup.CS, CsAdminServiceIntTestSetup.Port.UI);

        var newUrl = request.url().newBuilder()
                .host(csContainer.host())
                .port(csContainer.port())
                .build();

        request = request.newBuilder()
                .url(newUrl)
                .build();


        return chain.proceed(request);
    }

}

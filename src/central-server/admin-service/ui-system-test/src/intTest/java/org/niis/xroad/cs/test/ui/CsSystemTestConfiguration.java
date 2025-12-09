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
package org.niis.xroad.cs.test.ui;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.test.ui.api.FeignManagementRequestsApi;
import org.niis.xroad.test.framework.core.feign.FeignFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.niis.xroad.cs.test.ui.CsSystemTestContainerSetup.CS;
import static org.niis.xroad.cs.test.ui.CsSystemTestContainerSetup.Port.UI;


@Configuration
@RequiredArgsConstructor
public class CsSystemTestConfiguration {
    private final FeignFactory feignFactory;
    private final CsSystemTestContainerSetup testSetup;

    private String getBaseUrl() {
        var container = testSetup.getContainerMapping(CS, UI);

        return "https://" + container.host() + ":" + container.port() + "/api/v1";
    }

    @Bean
    public FeignManagementRequestsApi feignManagementRequestsApi() {
        return feignFactory.createClient(FeignManagementRequestsApi.class, getBaseUrl());
    }

}

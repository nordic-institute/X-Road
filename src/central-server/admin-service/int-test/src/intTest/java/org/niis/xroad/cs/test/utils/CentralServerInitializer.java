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
package org.niis.xroad.cs.test.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.openapi.model.InitialServerConfDto;
import org.niis.xroad.cs.test.api.FeignInitializationApi;
import org.niis.xroad.cs.test.container.service.MockServerService;
import org.springframework.stereotype.Component;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.niis.xroad.cs.test.glue.CommonStepDefs.TokenType.SYSTEM_ADMINISTRATOR;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@Component
@Slf4j
@RequiredArgsConstructor
public class CentralServerInitializer {
    private final MockServerService mockServerService;
    private final FeignInitializationApi initializationApi;

    public void initializeWithDefaults() {
        log.info("Initializing CentralServer with default configuration");

        mockSignerInit();
        var authHeader = SYSTEM_ADMINISTRATOR.getHeaderToken();
        var request = new InitialServerConfDto();
        request.setInstanceIdentifier("CS");
        request.setCentralServerAddress("cs");
        request.setSoftwareTokenPin("1234-VALID");

        initializationApi.initCentralServerWithHeader(request, authHeader);
    }

    private void mockSignerInit() {
        mockServerService.client()
                .when(request()
                        .withMethod("PUT")
                        .withPath("/initSoftwareToken/"))
                .respond(response().withStatusCode(NO_CONTENT.value()));
    }
}

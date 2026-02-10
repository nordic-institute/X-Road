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
package org.niis.xroad.cs.registrationservice.controller;

import ee.ria.xroad.common.request.AuthCertRegRequestType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managementrequest.ManagementRequestSoapExecutor;
import org.niis.xroad.cs.registrationservice.service.AdminApiService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_REQUEST;

/**
 * Handles security server authentication certificate registration requests from
 * security servers.
 * <p>
 * Security note: This API is exposed publicly (security servers not yet part of this X-Road instance need to be
 * able to send the registration request).
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class RegistrationRequestController {
    private final ManagementRequestSoapExecutor managementRequestSoapExecutor;
    private final AdminApiService adminApiService;

    @PostMapping(path = {"/managementservice", "/managementservice/"},
            produces = {MediaType.TEXT_XML_VALUE},
            consumes = {MediaType.MULTIPART_RELATED_VALUE})
    public ResponseEntity<String> register(@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, InputStream body) {
        return managementRequestSoapExecutor.process(contentType, body,
                result -> {
                    var authRequest = result.getRequest(AuthCertRegRequestType.class)
                            .orElseThrow(() -> XrdRuntimeException.systemException(INVALID_REQUEST, "AuthCertRegRequest is missing"));

                    log.debug("Making a registration request for {}", authRequest.getServer());

                    if (BooleanUtils.isTrue(authRequest.isDryRun())) {
                        log.info("Processed dry-run registration request for {}", authRequest.getServer());
                        return 0;
                    }

                    var requestId = adminApiService.addRegistrationRequest(
                            authRequest.getServer(),
                            authRequest.getAddress(),
                            authRequest.getAuthCert());

                    log.info("Processed registration request {} for {}", requestId, authRequest.getServer());
                    return requestId;
                });
    }

}

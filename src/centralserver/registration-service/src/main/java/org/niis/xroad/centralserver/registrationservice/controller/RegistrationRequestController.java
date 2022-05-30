/**
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
package org.niis.xroad.centralserver.registrationservice.controller;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.message.SoapFault;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;
import org.niis.xroad.centralserver.registrationservice.request.ManagementRequestUtil;
import org.niis.xroad.centralserver.registrationservice.request.ManagementRequestVerifier;
import org.niis.xroad.centralserver.registrationservice.service.AdminApiService;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/**
 * Handles security server authentication certificate registration requests from
 * security servers.
 *
 * Security note: This API is exposed publicly (security servers not yet part of this X-Road instance need to be
 * able to send the registration request).
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class RegistrationRequestController {

    public static final int MAX_REQUEST_SIZE = 100_000;
    private final AdminApiService adminApiService;

    @ResponseBody
    @PostMapping(path = "/managementservice",
            produces = {MediaType.TEXT_XML_VALUE},
            consumes = {MediaType.MULTIPART_RELATED_VALUE})
    public ResponseEntity<String> register(@RequestHeader("Content-Type") String contentType, InputStream body) {

        try (var bos = new BoundedInputStream(body, MAX_REQUEST_SIZE)) {
            var result = ManagementRequestVerifier.readRequest(contentType, bos);

            var authRequest = result.getAuthCertRegRequest();

            log.debug("Making a registration request for {}", authRequest.getServer());

            var requestId = adminApiService.addRegistrationRequest(
                    authRequest.getServer(),
                    authRequest.getAddress(),
                    authRequest.getAuthCert());

            log.info("Processed registration request {} for {}", requestId, authRequest.getServer());

            return ResponseEntity
                    .ok()
                    .header("Pragma", "no-cache").header("Expires", "0")
                    .cacheControl(CacheControl.noStore())
                    .body(ManagementRequestUtil.toResponse(result.getSoapMessage(), requestId).getXml());

        } catch (Exception e) {
            var ex = ErrorCodes.translateException(e);
            // override the detail code with traceId
            ex.setFaultDetail(MDC.get("traceId"));

            if (log.isDebugEnabled() || !(e instanceof CodedException)) {
                log.error("Registration failed", ex);
            } else {
                var cause = (ex.getCause() == null) ? "" : ", nested exception is" + ex.getCause().toString();
                log.error("Registration failed: {}{}", ex.getMessage(), cause);
            }
            return ResponseEntity
                    .internalServerError()
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .cacheControl(CacheControl.noStore())
                    .body(SoapFault.createFaultXml(ex));
        }
    }
}

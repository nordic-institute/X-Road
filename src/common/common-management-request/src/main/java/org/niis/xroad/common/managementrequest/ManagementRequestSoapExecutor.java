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
package org.niis.xroad.common.managementrequest;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.message.SoapFault;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestUtil;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestVerifier;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.util.function.ToIntFunction;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ManagementRequestSoapExecutor {
    public static final int MAX_REQUEST_SIZE = 100_000;

    public static ResponseEntity<String> process(String contentType, InputStream body,
                                          ToIntFunction<ManagementRequestVerifier.Result> onSuccess) {
        try (var bos = new BoundedInputStream(body, MAX_REQUEST_SIZE)) {
            var verificationResult = ManagementRequestVerifier.readRequest(contentType, bos);

            var createdRequestId = onSuccess.applyAsInt(verificationResult);

            var responseBody = ManagementRequestUtil.toResponse(verificationResult.getSoapMessage(), createdRequestId).getXml();
            return disableCache(ResponseEntity.ok())
                    .body(responseBody);
        } catch (Exception e) {
            var ex = ErrorCodes.translateException(e);
            // override the detail code with traceId
            ex.setFaultDetail(MDC.get("traceId"));

            if (log.isDebugEnabled() || !(e instanceof CodedException)) {
                log.error("ManagementRequest failed", ex);
            } else {
                var cause = (ex.getCause() == null) ? "" : ", nested exception is" + ex.getCause().toString();
                log.error("ManagementRequest failed: {}{}", ex.getMessage(), cause);
            }
            return disableCache(ResponseEntity.internalServerError())
                    .body(SoapFault.createFaultXml(ex));
        }
    }

    private static ResponseEntity.BodyBuilder disableCache(ResponseEntity.BodyBuilder builder) {
        return builder.header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .cacheControl(CacheControl.noStore());
    }
}

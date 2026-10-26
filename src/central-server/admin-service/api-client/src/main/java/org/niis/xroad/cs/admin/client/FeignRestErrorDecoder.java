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
package org.niis.xroad.cs.admin.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.cs.openapi.model.ErrorInfoDto;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.REQUEST_TOO_LARGE;
import static org.niis.xroad.common.core.exception.ErrorCode.SYSTEM_DID_FINAL_CONFLICT;
import static org.niis.xroad.common.core.exception.ErrorCode.SYSTEM_DID_FIXABLE_CONFLICT;
import static org.niis.xroad.common.core.exception.ErrorCode.SYSTEM_DID_NOT_RESOLVED;

@Component
@RequiredArgsConstructor
public class FeignRestErrorDecoder implements ErrorDecoder {
    public static final String REQUEST_FAILED = "Management request failed";

    private static final Map<String, ErrorCode> OUTCOME_CODES = Map.of(
            "management_request_did_final_conflict", SYSTEM_DID_FINAL_CONFLICT,
            "management_request_did_fixable_conflict", SYSTEM_DID_FIXABLE_CONFLICT,
            "management_request_did_not_resolved", SYSTEM_DID_NOT_RESOLVED,
            "management_request_too_large", REQUEST_TOO_LARGE);

    private final ObjectMapper mapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.body() == null) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, REQUEST_FAILED);
        }
        try (var inputStream = response.body().asInputStream()) {
            var errorInfo = mapper.readValue(inputStream, ErrorInfoDto.class);
            var adminCode = errorInfo.getError() != null ? errorInfo.getError().getCode() : null;
            var outcomeCode = adminCode == null ? null : OUTCOME_CODES.get(adminCode);
            if (outcomeCode != null) {
                var metadata = errorInfo.getError().getMetadata();
                var reason = metadata == null || metadata.isEmpty() ? adminCode : metadata.getFirst();
                throw XrdRuntimeException.systemException(outcomeCode).details(reason).build();
            }
            var detail = adminCode != null ? adminCode : REQUEST_FAILED;
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, "%s with statusCode %s", detail, errorInfo.getStatus());
        } catch (IOException | JacksonException ex) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, ex, REQUEST_FAILED);
        }

    }
}

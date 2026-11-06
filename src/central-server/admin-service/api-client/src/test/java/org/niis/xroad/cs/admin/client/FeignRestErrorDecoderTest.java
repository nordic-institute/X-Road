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

package org.niis.xroad.cs.admin.client;

import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeignRestErrorDecoderTest {

    private final FeignRestErrorDecoder decoder = new FeignRestErrorDecoder(JsonMapper.builder().build());

    private static Response responseWithCode(String adminCode) {
        return responseWithBody("{\"status\":400,\"error\":{\"code\":\"%s\"}}".formatted(adminCode));
    }

    private static Response responseWithBody(String body) {
        return Response.builder()
                .status(400)
                .request(Request.create(Request.HttpMethod.POST, "http://localhost/api", Collections.emptyMap(), null, null, null))
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    @Test
    void mapsTheRequestTooLargeMessageToTheSharedErrorCode() {
        assertThatThrownBy(() -> decoder.decode("method", responseWithCode("management_request_too_large")))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.REQUEST_TOO_LARGE.code()));
    }

    @Test
    void forwardsTheFirstMetadataElementOfTheRequestTooLargeMessageAsTheFaultDetails() {
        var body = "{\"status\":400,\"error\":{\"code\":\"management_request_too_large\",\"metadata\":[\"100\"]}}";

        assertThatThrownBy(() -> decoder.decode("method", responseWithBody(body)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.REQUEST_TOO_LARGE.code());
                    assertThat(e.getDetails()).isEqualTo("100");
                });
    }

    @Test
    void anUnknownMessageStaysAnInternalError() {
        assertThatThrownBy(() -> decoder.decode("method", responseWithCode("management_request_unknown")))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.code()));
    }
}

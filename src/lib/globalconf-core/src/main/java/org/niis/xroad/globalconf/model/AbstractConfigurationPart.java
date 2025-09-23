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
package org.niis.xroad.globalconf.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.Map;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TRANSFER_ENCODING;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TYPE;

@RequiredArgsConstructor
public abstract class AbstractConfigurationPart {

    @Getter
    protected final Map<String, String> parameters;

    public String getContentType() {
        return parameters.get(HEADER_CONTENT_TYPE);
    }

    public String getContentTransferEncoding() {
        return parameters.get(HEADER_CONTENT_TRANSFER_ENCODING);
    }

    protected static void verifyFieldExists(Map<String, String> headers,
                                            String fieldName) {
        verifyFieldExists(headers, fieldName, null);
    }

    protected static void verifyFieldExists(Map<String, String> headers,
                                            String fieldName, String expectedValue) {
        String value = headers.get(fieldName);
        if (StringUtils.isBlank(value)) {
            throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_HEADER_FIELD_MISSING)
                    .details("Missing field %s".formatted(fieldName))
                    .metadataItems(fieldName)
                    .build();
        }

        if (expectedValue != null && !expectedValue.equals(value)) {
            throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_HEADER_FIELD_WRONG_VALUE)
                    .details("Field %s must have value %s".formatted(fieldName, expectedValue))
                    .metadataItems(fieldName, value)
                    .build();
        }
    }
}

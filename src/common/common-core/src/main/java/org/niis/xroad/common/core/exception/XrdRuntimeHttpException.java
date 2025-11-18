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

package org.niis.xroad.common.core.exception;

import ee.ria.xroad.common.HttpStatus;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class XrdRuntimeHttpException extends XrdRuntimeException implements HttpStatusAware {

    private final HttpStatus httpStatus;

    XrdRuntimeHttpException(@NonNull String identifier, @NonNull ExceptionCategory category, @NonNull String errorCode,
                            @NonNull List<String> errorCodeMetadata, ErrorOrigin origin, String details,
                            @NonNull HttpStatus httpStatus) {
        super(identifier, category, errorCode, errorCodeMetadata, origin, details);
        this.httpStatus = httpStatus;
    }

    XrdRuntimeHttpException(@NonNull Throwable cause, @NonNull String identifier, @NonNull ExceptionCategory category,
                            @NonNull String errorCode, @NonNull List<String> errorCodeMetadata, ErrorOrigin origin,
                            String details, @NonNull HttpStatus httpStatus) {
        super(cause, identifier, category, errorCode, errorCodeMetadata, origin, details);
        this.httpStatus = httpStatus;
    }

    @Override
    public Optional<HttpStatus> getHttpStatus() {
        return Optional.of(this.httpStatus);
    }

    @Override
    public XrdRuntimeHttpException withPrefix(String... prefixes) {
        //TODO consider keeping prefix separately instead of modifying the code
        var prefix = StringUtils.join(prefixes, ".");

        if (!getCode().startsWith(prefix)) {
            if (getCause() != null) {
                return new XrdRuntimeHttpException(
                        getCause(),
                        getIdentifier(),
                        getCategory(),
                        prefix + "." + getCode(),
                        getErrorCodeMetadata(),
                        getOrigin(),
                        getDetails(),
                        httpStatus);
            } else {
                return new XrdRuntimeHttpException(
                        getIdentifier(),
                        getCategory(),
                        prefix + "." + getCode(),
                        getErrorCodeMetadata(),
                        getOrigin(),
                        getDetails(),
                        httpStatus);
            }
        }
        return this;
    }

    public static XrdRuntimeHttpExceptionBuilder from(XrdRuntimeException ex) {
        return XrdRuntimeHttpExceptionBuilder.from(ex);
    }

    public static XrdRuntimeHttpExceptionBuilder builder(DeviationBuilder.ErrorDeviationBuilder error) {
        return new XrdRuntimeHttpExceptionBuilder(ExceptionCategory.SYSTEM, error);
    }

    public static class XrdRuntimeHttpExceptionBuilder extends XrdRuntimeExceptionBuilder<XrdRuntimeHttpExceptionBuilder> {

        private HttpStatus httpStatusValue;

        public XrdRuntimeHttpExceptionBuilder(ExceptionCategory category, DeviationBuilder.ErrorDeviationBuilder errorDeviation) {
            super(category, errorDeviation);
        }

        public XrdRuntimeHttpExceptionBuilder httpStatus(HttpStatus httpStatus) {
            this.httpStatusValue = httpStatus;
            return this;
        }

        @Override
        public XrdRuntimeHttpException build() {
            if (identifier == null) {
                identifier = UUID.randomUUID().toString();
            }

            var deviation = errorDeviation.build(metadataItems);
            if (cause != null) {
                return new XrdRuntimeHttpException(
                        cause,
                        identifier,
                        category,
                        resolveErrorCode(),
                        deviation.metadata(),
                        origin,
                        details,
                        httpStatusValue);
            }
            return new XrdRuntimeHttpException(
                    identifier,
                    category,
                    resolveErrorCode(),
                    deviation.metadata(),
                    origin,
                    details,
                    httpStatusValue);
        }

        static XrdRuntimeHttpExceptionBuilder from(XrdRuntimeException ex) {
            return new XrdRuntimeHttpExceptionBuilder(ex.getCategory(), ErrorCode.withCode(ex.getErrorCode()))
                    .identifier(ex.getIdentifier())
                    .cause(ex.getCause())
                    .metadataItems(ex.getErrorCodeMetadata())
                    .details(ex.getDetails())
                    .origin(ex.getOrigin());
        }

    }

}

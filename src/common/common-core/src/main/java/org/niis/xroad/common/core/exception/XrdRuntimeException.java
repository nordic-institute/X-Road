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
package org.niis.xroad.common.core.exception;

import ee.ria.xroad.common.CodedException;

import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.soap.SOAPException;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.nio.channels.UnresolvedAddressException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

/**
 * A highly customizable exception class for X-Road use cases.
 * <p>
 * TODO Until migration from CodedException is complete, this class
 *       extends CodedException. In the future, it will be a standalone.
 */
@Getter
@Slf4j
public sealed class XrdRuntimeException extends CodedException
        permits XrdRuntimeHttpException {

    private final String identifier;
    private final ExceptionCategory category;

    private final String errorCode;
    private final List<String> errorCodeMetadata;

    private final String details;
    private final ErrorOrigin origin;

    XrdRuntimeException(@NonNull String identifier,
                        @NonNull ExceptionCategory category,
                        @NonNull String errorCode,
                        @NonNull List<String> errorCodeMetadata,
                        ErrorOrigin origin,
                        String details) {
        super(errorCode, details);
        this.identifier = identifier;
        this.translationCode = errorCode;
        this.category = category;
        this.errorCode = errorCode;
        this.errorCodeMetadata = errorCodeMetadata;
        this.origin = origin;
        this.details = details;
    }

    XrdRuntimeException(@NonNull Throwable cause,
                        @NonNull String identifier,
                        @NonNull ExceptionCategory category,
                        @NonNull String errorCode,
                        @NonNull List<String> errorCodeMetadata,
                        ErrorOrigin origin,
                        String details) {
        super(errorCode, cause, details);
        this.identifier = identifier;
        this.translationCode = errorCode;
        this.category = category;
        this.errorCode = errorCode;
        this.errorCodeMetadata = errorCodeMetadata;
        this.origin = origin;
        this.details = details;
    }

    @Override
    public String toString() {
        String id = identifier != null ? identifier : "unknown";
        String cat = category != null ? category.toString() : "UNKNOWN";

        var message = "[%s] [%s] %s".formatted(id, cat, getCode());

        if (errorCodeMetadata != null && !errorCodeMetadata.isEmpty()) {
            message += " (%s)".formatted(String.join(", ", errorCodeMetadata));
        }

        if (details != null && !details.isBlank()) {
            message += ": %s".formatted(details);
        }

        return message;
    }

    @Override
    public String getMessage() {
        return toString();
    }

    @Override
    public String getFaultString() {
        return details;
    }

    public String getCode() {
        return errorCode;
    }

    public boolean isCausedBy(ErrorCode expectedErrorCode) {
        if (errorCode.contains(".")) {
            return errorCode.endsWith("." + expectedErrorCode.code());
        }

        return errorCode.equals(expectedErrorCode.code());
    }

    public boolean originatesFrom(ErrorOrigin expectedOrigin) {
        if (origin != null) {
            return origin == expectedOrigin;
        }

        return errorCode.startsWith(expectedOrigin.toPrefix());
    }

    @Override
    public String getFaultCode() {
        return getCode();
    }

    @Override
    public XrdRuntimeException withPrefix(String... prefixes) {
        //TODO consider keeping prefix separately instead of modifying the code
        var prefix = StringUtils.join(prefixes, ".");

        if (!getCode().startsWith(prefix)) {
            if (getCause() != null) {
                return new XrdRuntimeException(
                        getCause(),
                        getIdentifier(),
                        getCategory(),
                        prefix + "." + getCode(),
                        errorCodeMetadata,
                        getOrigin(),
                        getDetails());
            } else {
                return new XrdRuntimeException(
                        getIdentifier(),
                        getCategory(),
                        prefix + "." + getCode(),
                        errorCodeMetadata,
                        getOrigin(),
                        getDetails());
            }
        }
        return this;
    }

    /**
     * Creates a system exception builder for system-level errors.
     *
     * @param error the error deviation builder
     * @return a new builder instance
     * @throws IllegalArgumentException if error is null
     */
    public static XrdRuntimeExceptionBuilder systemException(DeviationBuilder.ErrorDeviationBuilder error) {
        if (error == null) {
            throw new IllegalArgumentException("ErrorDeviationBuilder cannot be null");
        }
        return new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, error);
    }

    /**
     * Creates a business exception builder for business logic errors.
     *
     * @param error the error deviation builder
     * @return a new builder instance
     * @throws IllegalArgumentException if error is null
     */
    @Deprecated(forRemoval = true)
    public static XrdRuntimeExceptionBuilder businessException(DeviationBuilder.ErrorDeviationBuilder error) {
        if (error == null) {
            throw new IllegalArgumentException("ErrorDeviationBuilder cannot be null");
        }
        return new XrdRuntimeExceptionBuilder(ExceptionCategory.BUSINESS, error);
    }

    /**
     * Creates a validation exception builder for validation errors.
     *
     * @param error the error deviation builder
     * @return a new builder instance
     * @throws IllegalArgumentException if error is null
     */
    @Deprecated(forRemoval = true)
    public static XrdRuntimeExceptionBuilder validationException(DeviationBuilder.ErrorDeviationBuilder error) {
        if (error == null) {
            throw new IllegalArgumentException("ErrorDeviationBuilder cannot be null");
        }
        return new XrdRuntimeExceptionBuilder(ExceptionCategory.VALIDATION, error);
    }

    /**
     * Translates technical exceptions to proxy exceptions with
     * the appropriate error code.
     *
     * @param ex the exception
     * @return translated XrdRuntimeException
     * @throws IllegalArgumentException if ex is null
     */
    public static XrdRuntimeException systemException(Throwable ex) {
        return switch (ex) {
            case null -> throw new IllegalArgumentException("Exception cannot be null");
            case XrdRuntimeException xrdEx -> xrdEx;
            case CodedException cex -> new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, ErrorCode.withCode(cex.getFaultCode()))
                    .cause(ex)
                    .details(cex.getFaultString())
                    .build();
            default -> new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, resolveExceptionCode(ex))
                    .cause(ex)
                    .details(ex.getMessage())
                    .build();
        };
    }

    public static XrdRuntimeException systemException(DeviationBuilder.ErrorDeviationBuilder errorCode, String details, Object... params) {
        return new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, errorCode)
                .details(details.formatted(params))
                .build();
    }

    public static XrdRuntimeException systemException(DeviationBuilder.ErrorDeviationBuilder errorCode, Throwable cause, String details,
                                                      Object... params) {
        return new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, errorCode)
                .cause(cause)
                .details(details.formatted(params))
                .build();
    }

    public static XrdRuntimeException systemException(DeviationBuilder.ErrorDeviationBuilder errorCode, Throwable cause) {
        return new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, errorCode)
                .cause(cause)
                .build();
    }

    public static XrdRuntimeException systemInternalError(String details) {
        return new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, INTERNAL_ERROR)
                .details(details)
                .build();
    }

    public static XrdRuntimeException systemInternalError(String details, Throwable ex) {
        if (ex instanceof XrdRuntimeException xrdEx) {
            return xrdEx;
        }
        return new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, INTERNAL_ERROR)
                .details(details)
                .cause(ex)
                .build();
    }

    /**
     * Resolves the appropriate error code based on the exception type.
     * Maps common technical exceptions to X-Road error codes.
     *
     * @param ex the exception to analyze
     * @return the appropriate ErrorCodes enum value
     */
    @ArchUnitSuppressed("NoVanillaExceptions")
    private static DeviationBuilder.ErrorDeviationBuilder resolveExceptionCode(Throwable ex) {
        return switch (ex) {
            case CodedException cex -> ErrorCode.withCode(cex.getFaultCode());
            case UnknownHostException ignored -> ErrorCode.UNKNOWN_HOST;
            case MalformedURLException ignored -> ErrorCode.NETWORK_ERROR;
            case SocketException ignored -> ErrorCode.NETWORK_ERROR;
            case UnknownServiceException ignored -> ErrorCode.NETWORK_ERROR;
            case UnresolvedAddressException ignored -> ErrorCode.NETWORK_ERROR;
            case IOException ignored -> ErrorCode.IO_ERROR;
            case SQLException ignored -> ErrorCode.DATABASE_ERROR;
            case CertificateException ignored -> ErrorCode.INCORRECT_CERTIFICATE;
            case SOAPException ignored -> ErrorCode.INVALID_SOAP;
            case SAXException ignored -> ErrorCode.INVALID_XML;
            case UnmarshalException ue when isAccessorException(ue.getCause()) -> resolveExceptionCode(ue.getCause());
            case Exception me when isMimeException(me) -> ErrorCode.MIME_PARSING_FAILED;
            case Exception ae when isAccessorException(ae) && ae.getCause() instanceof CodedException cex ->
                    ErrorCode.withCode(cex.getFaultCode());
            default -> INTERNAL_ERROR;
        };
    }

    /**
     * Checks if the exception is an AccessorException from JAXB runtime.
     *
     * @param ex the exception to check
     * @return true if it's an AccessorException
     */
    private static boolean isAccessorException(Throwable ex) {
        return ex != null && ex.getClass().getName().equals("org.glassfish.jaxb.runtime.api.AccessorException");
    }

    /**
     * Checks if the exception is a MimeException from Apache James.
     *
     * @param ex the exception to check
     * @return true if it's a MimeException
     */
    private static boolean isMimeException(Throwable ex) {
        return ex != null && ex.getClass().getName().equals("org.apache.james.mime4j.MimeException");
    }

}

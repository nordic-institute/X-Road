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
import ee.ria.xroad.common.HttpStatus;

import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.soap.SOAPException;
import lombok.Getter;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.nio.channels.UnresolvedAddressException;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.UUID;

/**
 * A highly customizable exception class for X-Road use cases.
 * <p>
 * TODO Until migration from CodedException is complete, this class
 *       extends CodedException. In the future, it will be a standalone.
 */
@Getter
public final class XrdRuntimeException extends CodedException implements HttpStatusAware {

    private final String identifier;
    private final ExceptionCategory category;
    private final ErrorDeviation errorDeviation;
    private final String details;
    private final boolean thrownRemotely;
    private final HttpStatus httpStatus;

    XrdRuntimeException(String identifier,
                        ExceptionCategory category,
                        boolean thrownRemotely,
                        ErrorDeviation errorDeviation,
                        String details,
                        HttpStatus httpStatus) {
        super(errorDeviation.code(), details);
        this.identifier = identifier;
        this.translationCode = errorDeviation.code();
        this.category = category;
        this.thrownRemotely = thrownRemotely;
        this.errorDeviation = errorDeviation;
        this.details = details;
        this.httpStatus = httpStatus;
    }

    XrdRuntimeException(Throwable cause,
                        String identifier,
                        ExceptionCategory category,
                        boolean thrownRemotely,
                        ErrorDeviation errorDeviation,
                        String details,
                        HttpStatus httpStatus) {
        super(errorDeviation.code(), cause, details);
        this.identifier = identifier;
        this.translationCode = errorDeviation.code();
        this.category = category;
        this.thrownRemotely = thrownRemotely;
        this.errorDeviation = errorDeviation;
        this.details = details;
        this.httpStatus = httpStatus;
    }

    XrdRuntimeException(Throwable cause,
                        String identifier,
                        ExceptionCategory category,
                        boolean thrownRemotely,
                        ErrorDeviation errorDeviation,
                        HttpStatus httpStatus) {
        super(errorDeviation.code(), cause);
        this.identifier = identifier;
        this.category = category;
        this.thrownRemotely = thrownRemotely;
        this.errorDeviation = errorDeviation;
        this.details = null;
        this.httpStatus = httpStatus;
    }

    @Override
    public String toString() {
        String id = identifier != null ? identifier : "unknown";
        String cat = category != null ? category.toString() : "UNKNOWN";
        String code = errorDeviation != null ? errorDeviation.code() : "unknown_error";

        var message = "[%s] [%s] %s".formatted(id, cat, code);

        if (errorDeviation != null && errorDeviation.metadata() != null && !errorDeviation.metadata().isEmpty()) {
            message += " (%s)".formatted(String.join(", ", errorDeviation.metadata()));
        }

        if (details != null && !details.isBlank()) {
            message += ": %s".formatted(details);
        }

        if (thrownRemotely) {
            message += " (thrown remotely)";
        }

        return message;
    }

    @Override
    public String getMessage() {
        return toString();
    }

    public Optional<HttpStatus> getHttpStatus() {
        return Optional.ofNullable(httpStatus);
    }

    /**
     * Creates a system exception builder for system-level errors.
     *
     * @param error the error deviation builder
     * @return a new builder instance
     * @throws IllegalArgumentException if error is null
     */
    public static Builder systemException(DeviationBuilder.ErrorDeviationBuilder error) {
        if (error == null) {
            throw new IllegalArgumentException("ErrorDeviationBuilder cannot be null");
        }
        return new Builder(ExceptionCategory.SYSTEM, error);
    }

    /**
     * Creates a business exception builder for business logic errors.
     *
     * @param error the error deviation builder
     * @return a new builder instance
     * @throws IllegalArgumentException if error is null
     */
    public static Builder businessException(DeviationBuilder.ErrorDeviationBuilder error) {
        if (error == null) {
            throw new IllegalArgumentException("ErrorDeviationBuilder cannot be null");
        }
        return new Builder(ExceptionCategory.BUSINESS, error);
    }

    /**
     * Creates a validation exception builder for validation errors.
     *
     * @param error the error deviation builder
     * @return a new builder instance
     * @throws IllegalArgumentException if error is null
     */
    public static Builder validationException(DeviationBuilder.ErrorDeviationBuilder error) {
        if (error == null) {
            throw new IllegalArgumentException("ErrorDeviationBuilder cannot be null");
        }
        return new Builder(ExceptionCategory.VALIDATION, error);
    }

    /**
     * Translates technical exceptions to proxy exceptions with
     * the appropriate error code.
     *
     * @param ex the exception
     * @return translated XrdRuntimeException
     * @throws IllegalArgumentException if ex is null
     */
    @SuppressWarnings("squid:S1872")
    public static XrdRuntimeException systemException(Throwable ex) {
        if (ex == null) {
            throw new IllegalArgumentException("Exception cannot be null");
        }
        if (ex instanceof XrdRuntimeException xrdEx) {
            return xrdEx;
        }
        return new Builder(ExceptionCategory.SYSTEM, resolveExceptionCode(ex))
                .cause(ex)
                .details(ex.getMessage())
                .build();
    }

    public static XrdRuntimeException systemInternalError(String details) {
        return new Builder(ExceptionCategory.SYSTEM, ErrorCodes.INTERNAL_ERROR)
                .details(details)
                .build();
    }

    public static XrdRuntimeException systemInternalError(String details, Throwable ex) {
        return new Builder(ExceptionCategory.SYSTEM, ErrorCodes.INTERNAL_ERROR)
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
    private static ErrorCodes resolveExceptionCode(Throwable ex) {
        return switch (ex) {
            case CodedException cex -> ErrorCodes.fromCode(cex.getFaultCode());
            case UnknownHostException ignored -> ErrorCodes.NETWORK_ERROR;
            case MalformedURLException ignored -> ErrorCodes.NETWORK_ERROR;
            case SocketException ignored -> ErrorCodes.NETWORK_ERROR;
            case UnknownServiceException ignored -> ErrorCodes.NETWORK_ERROR;
            case UnresolvedAddressException ignored -> ErrorCodes.NETWORK_ERROR;
            case IOException ignored -> ErrorCodes.IO_ERROR;
            case CertificateException ignored -> ErrorCodes.INCORRECT_CERTIFICATE;
            case SOAPException ignored -> ErrorCodes.INVALID_SOAP;
            case SAXException ignored -> ErrorCodes.INVALID_XML;
            case UnmarshalException ue when isAccessorException(ue.getCause()) -> resolveExceptionCode(ue.getCause());
            case Exception me when isMimeException(me) -> ErrorCodes.MIME_PARSING_FAILED;
            case Exception ae when isAccessorException(ae) && ae.getCause() instanceof CodedException cex ->
                    ErrorCodes.fromCode(cex.getFaultCode());
            default -> ErrorCodes.INTERNAL_ERROR;
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

    /**
     * Builder class for constructing XrdRuntimeException instances.
     * Provides a fluent API for setting exception properties.
     */
    @SuppressWarnings("checkstyle:HiddenField")
    public static class Builder {
        private Throwable cause;
        private String identifier;
        private final ExceptionCategory category;

        private final DeviationBuilder.ErrorDeviationBuilder errorDeviation;
        private Object[] metadataItems;

        private String details;
        private boolean thrownRemotely = false;
        private HttpStatus httpStatus;

        public Builder(ExceptionCategory category, DeviationBuilder.ErrorDeviationBuilder errorDeviation) {
            if (category == null) {
                throw new IllegalArgumentException("ExceptionCategory cannot be null");
            }
            if (errorDeviation == null) {
                throw new IllegalArgumentException("ErrorDeviationBuilder cannot be null");
            }

            this.category = category;
            this.errorDeviation = errorDeviation;
        }

        /**
         * Sets the cause of this exception.
         *
         * @param cause the underlying cause
         * @return this builder instance
         */
        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        /**
         * Sets a custom identifier for this exception.
         *
         * @param identifier the unique identifier
         * @return this builder instance
         * @throws IllegalArgumentException if identifier is null or blank
         */
        public Builder identifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Marks whether this exception was thrown remotely.
         *
         * @param thrownRemotely true if thrown remotely
         * @return this builder instance
         */
        public Builder thrownRemotely(boolean thrownRemotely) {
            this.thrownRemotely = thrownRemotely;
            return this;
        }

        /**
         * Sets metadata items for error deviation formatting.
         *
         * @param metadataItems variable arguments for metadata
         * @return this builder instance
         */
        public Builder metadataItems(Object... metadataItems) {
            this.metadataItems = metadataItems;
            return this;
        }

        /**
         * Sets additional details for this exception.
         *
         * @param details the detailed description
         * @return this builder instance
         */
        public Builder details(String details) {
            this.details = details;
            return this;
        }

        /**
         * Set the HTTP status for this exception.
         * This is optional and can be used to indicate the HTTP status code
         * that should be returned in a web context.
         *
         * @param httpStatus the HTTP status to set
         * @return this builder instance
         */
        public Builder httpStatus(HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        /**
         * Builds the XrdRuntimeException with all configured properties.
         * Generates a random UUID identifier if none was specified.
         *
         * @return the constructed exception
         * @throws IllegalStateException if required parameters are missing
         */
        public XrdRuntimeException build() {
            if (identifier == null) {
                identifier = UUID.randomUUID().toString();
            }

            if (cause != null) {
                if (details != null) {
                    return new XrdRuntimeException(
                            cause,
                            identifier,
                            category,
                            thrownRemotely,
                            errorDeviation.build(metadataItems),
                            details,
                            httpStatus);
                }
                return new XrdRuntimeException(
                        cause,
                        identifier,
                        category,
                        thrownRemotely,
                        errorDeviation.build(),
                        httpStatus);
            }
            return new XrdRuntimeException(
                    identifier,
                    category,
                    thrownRemotely,
                    errorDeviation.build(metadataItems),
                    details,
                    httpStatus);
        }
    }
}

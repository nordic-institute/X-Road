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

import java.util.UUID;

/**
 * Builder class for constructing XrdRuntimeException instances.
 * Provides a fluent API for setting exception properties.
 */
@SuppressWarnings({"checkstyle:HiddenField", "javaarchitecture:S7027"})
public class XrdRuntimeExceptionBuilder<T extends XrdRuntimeExceptionBuilder<T>> {
    protected Throwable cause;
    protected String identifier;

    protected final DeviationBuilder.ErrorDeviationBuilder errorDeviation;
    protected Object[] metadataItems;

    protected String details;
    protected ErrorOrigin origin;
    protected XrdRuntimeException.SoapFaultInfo soapFaultInfo;

    public XrdRuntimeExceptionBuilder(DeviationBuilder.ErrorDeviationBuilder errorDeviation) {
        if (errorDeviation == null) {
            throw new IllegalArgumentException("ErrorDeviationBuilder cannot be null");
        }

        this.errorDeviation = errorDeviation;
    }

    /**
     * Sets the cause of this exception.
     *
     * @param cause the underlying cause
     * @return this builder instance
     */
    public T cause(Throwable cause) {
        this.cause = cause;
        return (T) this;
    }

    /**
     * Sets a custom identifier for this exception.
     *
     * @param identifier the unique identifier
     * @return this builder instance
     * @throws IllegalArgumentException if identifier is null or blank
     */
    public T identifier(String identifier) {
        this.identifier = identifier;
        return (T) this;
    }

    /**
     * Sets metadata items for error deviation formatting.
     *
     * @param metadataItems variable arguments for metadata
     * @return this builder instance
     */
    public T metadataItems(Object... metadataItems) {
        this.metadataItems = metadataItems;
        return (T) this;
    }

    /**
     * Sets additional details for this exception.
     *
     * @param details the detailed description
     * @return this builder instance
     */
    public T details(String details) {
        this.details = details;
        return (T) this;
    }

    /**
     * Sets the origin of the error.
     *
     * @param origin the error origin
     * @return this builder instance
     */
    public T origin(ErrorOrigin origin) {
        this.origin = origin;
        return (T) this;
    }

    T soapFaultInfo(XrdRuntimeException.SoapFaultInfo soapFaultInfo) {
        this.soapFaultInfo = soapFaultInfo;
        return (T) this;
    }

    public T soapFaultInfo(String faultCode,
                           String faultString,
                           String faultActor,
                           String faultDetail,
                           String faultXml) {
        this.soapFaultInfo = new XrdRuntimeException.SoapFaultInfo(faultCode, faultString, faultActor, faultDetail, faultXml);
        return (T) this;
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

        var deviation = errorDeviation.build(metadataItems);
        return new XrdRuntimeException(
                cause,
                identifier,
                resolveErrorCode(),
                deviation.metadata(),
                origin,
                details,
                soapFaultInfo);
    }

    protected String resolveErrorCode() {
        if (origin != null) {
            return origin.toPrefix() + errorDeviation.code();
        }
        return errorDeviation.code();
    }

    public static XrdRuntimeExceptionBuilder from(XrdRuntimeException ex) {
        return new XrdRuntimeExceptionBuilder<>(ErrorCode.fromCode(ex.getErrorCode()))
                .identifier(ex.getIdentifier())
                .origin(ex.getOrigin())
                .details(ex.getDetails())
                .metadataItems(ex.getErrorCodeMetadata())
                .cause(ex.getCause())
                .soapFaultInfo(ex.getSoapFaultInfo());
    }
}

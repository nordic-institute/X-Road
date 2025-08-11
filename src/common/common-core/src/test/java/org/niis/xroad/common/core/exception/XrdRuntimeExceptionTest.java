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

import ee.ria.xroad.common.HttpStatus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XrdRuntimeExceptionTest {

    @Test
    void shouldCreateWellFormedErrorMessage() {
        String identifier = "test-identifier";
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCodes.INTERNAL_ERROR;
        String details = "This is a test error message.";
        boolean thrownRemotely = false;

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .identifier(identifier)
                .thrownRemotely(thrownRemotely)
                .details(details)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());
        assertFalse(exception.isThrownRemotely());

        String expectedMessage = "[test-identifier] [SYSTEM] internal_error: This is a test error message.";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateExceptionWithMetadata() {
        String identifier = "metadata-test";
        ExceptionCategory category = ExceptionCategory.VALIDATION;
        var errorDeviation = ErrorCodes.INVALID_CERTIFICATE;
        String details = "Certificate validation failed";
        boolean thrownRemotely = false;

        XrdRuntimeException exception = XrdRuntimeException.validationException(errorDeviation)
                .identifier(identifier)
                .thrownRemotely(thrownRemotely)
                .details(details)
                .metadataItems("certificate", "expired", "2024-01-01")
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build("certificate", "expired", "2024-01-01"), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());
        assertFalse(exception.isThrownRemotely());

        String expectedMessage = "[metadata-test] [VALIDATION] invalid_certificate (certificate, expired, 2024-01-01): Certificate validation failed";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateBusinessException() {
        String identifier = "business-test";
        ExceptionCategory category = ExceptionCategory.BUSINESS;
        var errorDeviation = ErrorCodes.DUPLICATE_ENTRY;
        String details = "User already exists";

        XrdRuntimeException exception = XrdRuntimeException.businessException(errorDeviation)
                .identifier(identifier)
                .details(details)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());
        assertFalse(exception.isThrownRemotely());

        String expectedMessage = "[business-test] [BUSINESS] duplicate_entry: User already exists";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateExceptionWithHttpStatus() {
        String identifier = "http-test";
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCodes.NOT_FOUND;
        String details = "Resource not found";
        HttpStatus httpStatus = HttpStatus.NOT_FOUND;

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .identifier(identifier)
                .details(details)
                .httpStatus(httpStatus)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());
        assertEquals(httpStatus, exception.getHttpStatus().orElse(null));

        String expectedMessage = "[http-test] [SYSTEM] not_found: Resource not found";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateExceptionWithCause() {
        String identifier = "cause-test";
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCodes.IO_ERROR;
        String details = "File operation failed";
        Throwable cause = new RuntimeException("Underlying cause");

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .identifier(identifier)
                .details(details)
                .cause(cause)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());
        assertEquals(cause, exception.getCause());

        String expectedMessage = "[cause-test] [SYSTEM] io_error: File operation failed";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateExceptionWithThrownRemotely() {
        String identifier = "remote-test";
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCodes.NETWORK_ERROR;
        String details = "Connection timeout";
        boolean thrownRemotely = true;

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .identifier(identifier)
                .details(details)
                .thrownRemotely(thrownRemotely)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());
        assertTrue(exception.isThrownRemotely());

        String expectedMessage = "[remote-test] [SYSTEM] network_error: Connection timeout (thrown remotely)";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateExceptionWithoutDetails() {
        String identifier = "no-details-test";
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCodes.INTERNAL_ERROR;

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .identifier(identifier)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertNull(exception.getDetails());
        assertFalse(exception.isThrownRemotely());

        String expectedMessage = "[no-details-test] [SYSTEM] internal_error";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateExceptionWithEmptyDetails() {
        String identifier = "empty-details-test";
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCodes.INTERNAL_ERROR;
        String details = "";

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .identifier(identifier)
                .details(details)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());
        assertFalse(exception.isThrownRemotely());

        // Empty details should not appear in toString() since they're blank
        String expectedMessage = "[empty-details-test] [SYSTEM] internal_error";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateExceptionWithBlankDetails() {
        String identifier = "blank-details-test";
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCodes.INTERNAL_ERROR;
        String details = "   ";

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .identifier(identifier)
                .details(details)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());
        assertFalse(exception.isThrownRemotely());

        // Blank details should not appear in toString() since they're blank
        String expectedMessage = "[blank-details-test] [SYSTEM] internal_error";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateExceptionWithMetadataOnly() {
        String identifier = "metadata-only-test";
        ExceptionCategory category = ExceptionCategory.VALIDATION;
        var errorDeviation = ErrorCodes.INVALID_XML;

        XrdRuntimeException exception = XrdRuntimeException.validationException(errorDeviation)
                .identifier(identifier)
                .metadataItems("line", "42", "column", "15")
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build("line", "42", "column", "15"), exception.getErrorDeviation());
        assertNull(exception.getDetails());
        assertFalse(exception.isThrownRemotely());

        String expectedMessage = "[metadata-only-test] [VALIDATION] invalid_xml (line, 42, column, 15)";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldCreateExceptionWithNullHttpStatus() {
        String identifier = "null-http-test";
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCodes.INTERNAL_ERROR;
        String details = "No HTTP status";

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .identifier(identifier)
                .details(details)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());
        assertTrue(exception.getHttpStatus().isEmpty());

        String expectedMessage = "[null-http-test] [SYSTEM] internal_error: No HTTP status";
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldGenerateRandomIdentifierWhenNotProvided() {
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCodes.INTERNAL_ERROR;
        String details = "Auto-generated identifier";

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .details(details)
                .build();

        assertNotNull(exception.getIdentifier());
        assertFalse(exception.getIdentifier().isEmpty());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.build(), exception.getErrorDeviation());
        assertEquals(details, exception.getDetails());

        String expectedMessage = "[%s] [SYSTEM] internal_error: %s".formatted(exception.getIdentifier(), details);
        assertEquals(expectedMessage, exception.toString());
    }

    @Test
    void shouldHandleGetMessageSameAsToString() {
        String identifier = "message-test";
        var errorDeviation = ErrorCodes.INTERNAL_ERROR;
        String details = "Test message";

        XrdRuntimeException exception = XrdRuntimeException.systemException(errorDeviation)
                .identifier(identifier)
                .details(details)
                .build();

        String toStringResult = exception.toString();
        String getMessageResult = exception.getMessage();

        assertEquals(toStringResult, getMessageResult);
        assertEquals("[message-test] [SYSTEM] internal_error: Test message", toStringResult);
    }

    @Test
    void shouldHandleNullMetadataItemsArray() {
        XrdRuntimeException exception = XrdRuntimeException.systemException(ErrorCodes.INTERNAL_ERROR)
                .identifier("null-metadata-test")
                .metadataItems((Object[]) null)
                .build();

        assertNotNull(exception);
        assertEquals("[null-metadata-test] [SYSTEM] internal_error", exception.toString());
    }

    @Test
    void shouldHandleNullDetailsGracefully() {
        XrdRuntimeException exception = XrdRuntimeException.systemException(ErrorCodes.INTERNAL_ERROR)
                .identifier("null-details-test")
                .details(null)
                .build();

        assertNotNull(exception);
        assertNull(exception.getDetails());
        assertEquals("[null-details-test] [SYSTEM] internal_error", exception.toString());
    }


    @Test
    void shouldHandleNullExceptionInSystemException() {
        assertThrows(IllegalArgumentException.class, () -> {
            throw XrdRuntimeException.systemException((Throwable) null);
        });
    }

    @Test
    void shouldHandleNullErrorDeviationBuilderInFactoryMethods() {
        assertThrows(IllegalArgumentException.class, () -> {
            XrdRuntimeException.systemException((DeviationBuilder.ErrorDeviationBuilder) null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            XrdRuntimeException.businessException(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            XrdRuntimeException.validationException(null);
        });
    }

    @Test
    void shouldHandleNullBuilderParameters() {
        // Test that Builder constructor handles null parameters gracefully
        // Since validation was removed, these should not throw exceptions
        assertThrows(IllegalArgumentException.class, () -> {
            new XrdRuntimeException.Builder(null, null);
        });
    }

    @Test
    void shouldHandleNullIdentifierInBuilder() {
        // Test that setting null identifier doesn't throw exception
        XrdRuntimeException exception = XrdRuntimeException.systemException(ErrorCodes.INTERNAL_ERROR)
                .identifier(null)
                .build();

        assertNotNull(exception);
        // Should generate a random UUID when identifier is null
        assertNotNull(exception.getIdentifier());
        assertFalse(exception.getIdentifier().isEmpty());
    }

    @Test
    void shouldHandleBlankIdentifierInBuilder() {
        // Test that setting blank identifier doesn't throw exception
        XrdRuntimeException exception = XrdRuntimeException.systemException(ErrorCodes.INTERNAL_ERROR)
                .identifier("   ")
                .build();

        assertNotNull(exception);
        assertEquals("   ", exception.getIdentifier());
    }

    @Test
    void shouldHandleBlankDetailsInBuilder() {
        // Test that setting blank details is preserved (not converted to null)
        XrdRuntimeException exception = XrdRuntimeException.systemException(ErrorCodes.INTERNAL_ERROR)
                .identifier("blank-details-test")
                .details("   ")
                .build();

        assertNotNull(exception);
        assertEquals("   ", exception.getDetails());
        // Blank details should not appear in toString() since they're blank
        assertEquals("[blank-details-test] [SYSTEM] internal_error", exception.toString());
    }
}

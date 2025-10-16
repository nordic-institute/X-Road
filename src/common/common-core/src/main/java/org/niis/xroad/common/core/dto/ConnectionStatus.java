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
package org.niis.xroad.common.core.dto;

import ee.ria.xroad.common.DiagnosticStatus;

import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@ToString
public final class ConnectionStatus implements Serializable {
    private final DiagnosticStatus status;
    private final String errorCode;
    private final List<String> errorMetadata;       // empty when OK
    private final Map<String, List<String>> validationErrors; // empty when OK

    private ConnectionStatus(
            DiagnosticStatus status,
            String errorCode,
            List<String> errorMetadata,
            Map<String, List<String>> validationErrors
    ) {
        this.status = status;
        this.errorCode = errorCode;
        this.errorMetadata = List.copyOf(errorMetadata == null ? List.of() : errorMetadata);
        this.validationErrors = validationErrors == null
                ? Map.of()
                : validationErrors.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue())
                ));
    }

    public static ConnectionStatus ok() {
        return new ConnectionStatus(DiagnosticStatus.OK, null, List.of(), Map.of());
    }

    public static ConnectionStatus error(String errorCode, List<String> metadata) {
        return new ConnectionStatus(DiagnosticStatus.ERROR, errorCode,
                metadata == null ? List.of() : metadata, Map.of());
    }

    public static ConnectionStatus errorWithValidation(
            String errorCode,
            List<String> metadata,
            String validationKey,
            List<String> validationMetadata
    ) {
        Map<String, List<String>> ve = new HashMap<>();
        ve.put(validationKey, validationMetadata == null ? List.of() : List.copyOf(validationMetadata));
        return new ConnectionStatus(DiagnosticStatus.ERROR, errorCode,
                metadata == null ? List.of() : metadata, ve);
    }

    public static ConnectionStatus fromErrorAndValidation(
            String errorCode,
            List<String> metadata,
            String validationErrorCode,
            List<String> certValidationMetadata) {

        if (validationErrorCode == null) {
            return ConnectionStatus.error(errorCode, metadata);
        }

        List<String> validationMeta = (certValidationMetadata == null)
                ? List.of()
                : certValidationMetadata;

        return ConnectionStatus.errorWithValidation(
                errorCode,
                metadata,
                validationErrorCode,
                validationMeta
        );
    }
}

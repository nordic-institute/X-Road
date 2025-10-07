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
package org.niis.xroad.securityserver.restapi.converter;

import org.niis.xroad.common.core.dto.ConnectionStatus;

import org.niis.xroad.securityserver.restapi.openapi.model.CodeWithDetailsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionStatusDto;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthCertStatusConverter {
    public ConnectionStatusDto convert(ConnectionStatus connectionStatus) {
        return new ConnectionStatusDto()
                .error(getCodeWithDetailsDto(connectionStatus))
                .statusClass(DiagnosticStatusClassMapping.map(connectionStatus.getStatus()));
    }

    private CodeWithDetailsDto getCodeWithDetailsDto(ConnectionStatus connectionStatus) {
        return Optional.ofNullable(connectionStatus.getErrorCode())
                .map(errorCode -> new CodeWithDetailsDto(errorCode)
                        .metadata(connectionStatus.getErrorMetadata())
                        .validationErrors(connectionStatus.getValidationErrors()))
                .orElse(null);
    }
}

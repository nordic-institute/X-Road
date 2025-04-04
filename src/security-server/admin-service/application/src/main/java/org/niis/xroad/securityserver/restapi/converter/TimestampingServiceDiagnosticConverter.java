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
package org.niis.xroad.securityserver.restapi.converter;

import com.google.common.collect.Streams;
import org.niis.xroad.confclient.model.DiagnosticsStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClassDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingStatusDto;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converter for timestamping service diagnostics related data between openapi and service domain classes
 */
@Component
public class TimestampingServiceDiagnosticConverter {

    public TimestampingServiceDiagnosticsDto convert(DiagnosticsStatus diagnosticsStatus) {
        TimestampingServiceDiagnosticsDto timestampingServiceDiagnostics = new TimestampingServiceDiagnosticsDto();
        timestampingServiceDiagnostics.setUrl(diagnosticsStatus.getDescription());
        Optional<TimestampingStatusDto> statusCode = TimestampingStatusMapping.map(
                diagnosticsStatus.getReturnCode());
        timestampingServiceDiagnostics.setStatusCode(statusCode.orElse(null));
        Optional<DiagnosticStatusClassDto> statusClass = DiagnosticStatusClassMapping.map(
                diagnosticsStatus.getReturnCode());
        timestampingServiceDiagnostics.setStatusClass(statusClass.orElse(null));
        if (diagnosticsStatus.getPrevUpdate() != null) {
            timestampingServiceDiagnostics.setPrevUpdateAt(diagnosticsStatus.getPrevUpdate());
        }

        return timestampingServiceDiagnostics;
    }

    public Set<TimestampingServiceDiagnosticsDto> convert(Iterable<DiagnosticsStatus> statuses) {
        return Streams.stream(statuses)
                .map(this::convert)
                .collect(Collectors.toSet());
    }
}

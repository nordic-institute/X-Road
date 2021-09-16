/**
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

import ee.ria.xroad.common.DiagnosticsStatus;

import com.google.common.collect.Streams;
import org.niis.xroad.securityserver.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspResponder;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspResponderDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converter for certificate authority diagnostics related data between openapi and service domain classes
 */
@Component
public class OcspResponderDiagnosticConverter {

    public OcspResponderDiagnostics convert(
            OcspResponderDiagnosticsStatus ocspResponderDiagnosticsStatus) {
        OcspResponderDiagnostics ocspResponderDiagnostics = new OcspResponderDiagnostics();
        ocspResponderDiagnostics.setDistinguishedName(ocspResponderDiagnosticsStatus.getName());
        List<OcspResponder> ocspResponders = convertOcspResponders(
                ocspResponderDiagnosticsStatus.getOcspResponderStatusMap());
        ocspResponderDiagnostics.setOcspResponders(ocspResponders);
        return ocspResponderDiagnostics;
    }

    public Set<OcspResponderDiagnostics> convert(Iterable<OcspResponderDiagnosticsStatus> statuses)  {
        return Streams.stream(statuses)
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    private OcspResponder convertOcspResponder(DiagnosticsStatus diagnosticsStatus) {
        OcspResponder ocspResponder = new OcspResponder();
        ocspResponder.setUrl(diagnosticsStatus.getDescription());
        Optional<OcspStatus> statusCode = OcspStatusMapping.map(
                diagnosticsStatus.getReturnCode());
        ocspResponder.setStatusCode(statusCode.orElse(null));
        Optional<DiagnosticStatusClass> statusClass = DiagnosticStatusClassMapping.map(
                diagnosticsStatus.getReturnCode());
        ocspResponder.setStatusClass(statusClass.orElse(null));
        if (diagnosticsStatus.getPrevUpdate() != null) {
            ocspResponder.setPrevUpdateAt(diagnosticsStatus.getPrevUpdate());
        }
        ocspResponder.setNextUpdateAt(diagnosticsStatus.getNextUpdate());
        return ocspResponder;
    }

    private List<OcspResponder> convertOcspResponders(Iterable<DiagnosticsStatus> statuses)  {
        return Streams.stream(statuses)
                .map(this::convertOcspResponder)
                .collect(Collectors.toList());
    }
}

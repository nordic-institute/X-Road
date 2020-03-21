/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.DiagnosticsStatus;

import com.google.common.collect.Streams;
import org.niis.xroad.restapi.dto.CertificateAuthorityDiagnosticsStatus;
import org.niis.xroad.restapi.openapi.model.CertificateAuthorityDiagnostics;
import org.niis.xroad.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.restapi.openapi.model.OcspResponderDiagnostics;
import org.niis.xroad.restapi.openapi.model.OcspStatus;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converter for certificate authority diagnostics related data between openapi and service domain classes
 */
@Component
public class CertificateAuthorityDiagnosticConverter {

    public CertificateAuthorityDiagnostics convert(
            CertificateAuthorityDiagnosticsStatus certificateAuthorityDiagnosticsStatus) {
        CertificateAuthorityDiagnostics certificateAuthorityDiagnostics = new CertificateAuthorityDiagnostics();
        certificateAuthorityDiagnostics.setDistinguishedName(certificateAuthorityDiagnosticsStatus.getName());
        List<OcspResponderDiagnostics> ocspResponderDiagnostics = convertOcspResponderDiagnostics(
                certificateAuthorityDiagnosticsStatus.getOcspResponderStatusMap());
        certificateAuthorityDiagnostics.setOcspResponders(ocspResponderDiagnostics);
        return certificateAuthorityDiagnostics;
    }

    public List<CertificateAuthorityDiagnostics> convert(Iterable<CertificateAuthorityDiagnosticsStatus> statuses)  {
        return Streams.stream(statuses)
                .map(this::convert)
                .collect(Collectors.toList());
    }

    private OcspResponderDiagnostics convertOcspResponderDiagnostics(DiagnosticsStatus diagnosticsStatus) {
        OcspResponderDiagnostics ocspResponderDiagnostics = new OcspResponderDiagnostics();
        ocspResponderDiagnostics.setUrl(diagnosticsStatus.getDescription());
        Optional<OcspStatus> statusCode = OcspStatusMapping.map(
                diagnosticsStatus.getReturnCode());
        ocspResponderDiagnostics.setStatusCode(statusCode.orElse(null));
        Optional<DiagnosticStatusClass> statusClass = DiagnosticStatusClassMapping.map(
                diagnosticsStatus.getReturnCode());
        ocspResponderDiagnostics.setStatusClass(statusClass.orElse(null));
        if (diagnosticsStatus.getPrevUpdate() != null) {
            ocspResponderDiagnostics.setPrevUpdateAt(FormatUtils.fromLocalTimeToOffsetDateTime(
                    diagnosticsStatus.getPrevUpdate(), true));
        }
        ocspResponderDiagnostics.setNextUpdateAt(FormatUtils.fromLocalTimeToOffsetDateTime(
                diagnosticsStatus.getNextUpdate(), false));
        return ocspResponderDiagnostics;
    }

    private List<OcspResponderDiagnostics> convertOcspResponderDiagnostics(Iterable<DiagnosticsStatus> statuses)  {
        return Streams.stream(statuses)
                .map(this::convertOcspResponderDiagnostics)
                .collect(Collectors.toList());
    }
}

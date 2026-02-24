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

import ee.ria.xroad.common.DiagnosticsStatus;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.CostType;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.CaOcspDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CodeWithDetailsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CostTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspResponderDiagnosticsDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converter for certificate authority diagnostics related data between openapi and service domain classes
 */
@Component
@RequiredArgsConstructor
public class OcspResponderDiagnosticConverter {

    private final GlobalConfProvider globalConfProvider;

    public CaOcspDiagnosticsDto convert(
            OcspResponderDiagnosticsStatus ocspResponderDiagnosticsStatus) {
        CaOcspDiagnosticsDto ocspResponderDiagnostics = new CaOcspDiagnosticsDto();
        ocspResponderDiagnostics.setDistinguishedName(ocspResponderDiagnosticsStatus.getName());
        List<OcspResponderDiagnosticsDto> ocspResponders = convertOcspResponders(
                ocspResponderDiagnosticsStatus.getOcspResponderStatusMap());
        ocspResponderDiagnostics.setOcspResponders(ocspResponders);
        return ocspResponderDiagnostics;
    }

    public Set<CaOcspDiagnosticsDto> convert(Iterable<OcspResponderDiagnosticsStatus> statuses) {
        return Streams.stream(statuses)
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    private OcspResponderDiagnosticsDto convertOcspResponder(DiagnosticsStatus diagnosticsStatus) {
        OcspResponderDiagnosticsDto ocspResponder = new OcspResponderDiagnosticsDto();
        ocspResponder.setUrl(diagnosticsStatus.getDescription());
        ocspResponder.setCostType(getCostType(diagnosticsStatus));
        if (diagnosticsStatus.getErrorCode() != null) {
            ocspResponder.setError(new CodeWithDetailsDto(diagnosticsStatus.getErrorCode().code())
                    .metadata(diagnosticsStatus.getErrorCodeMetadata()));
        }
        ocspResponder.setStatusClass(DiagnosticStatusClassMapping.map(diagnosticsStatus.getStatus()));
        if (diagnosticsStatus.getPrevUpdate() != null) {
            ocspResponder.setPrevUpdateAt(diagnosticsStatus.getPrevUpdate());
        }
        ocspResponder.setNextUpdateAt(diagnosticsStatus.getNextUpdate());
        return ocspResponder;
    }

    private CostTypeDto getCostType(DiagnosticsStatus diagnosticsStatus) {
        CostType costType =
                globalConfProvider.getOcspResponderCostType(globalConfProvider.getInstanceIdentifier(), diagnosticsStatus.getDescription());
        return costType != null ? CostTypeDto.valueOf(costType.name()) : CostTypeDto.UNDEFINED;
    }

    private List<OcspResponderDiagnosticsDto> convertOcspResponders(Iterable<DiagnosticsStatus> statuses) {
        return Streams.stream(statuses)
                .map(this::convertOcspResponder)
                .collect(Collectors.toList());
    }
}

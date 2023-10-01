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
import org.niis.xroad.securityserver.restapi.dto.ApprovedCaDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateAuthority;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converter for CertificateAuthority related data between openapi and service domain classes
 */
@Component
public class CertificateAuthorityConverter {

    /**
     * convert ApprovedCaDto into openapi CertificateAuthority class
     * @param approvedCaDto
     * @return
     */
    public CertificateAuthority convert(ApprovedCaDto approvedCaDto) {
        CertificateAuthority ca = new CertificateAuthority();
        ca.setName(approvedCaDto.getName());
        ca.setAuthenticationOnly(Boolean.TRUE.equals(approvedCaDto.isAuthenticationOnly()));
        ca.setNotAfter(approvedCaDto.getNotAfter());
        ca.setIssuerDistinguishedName(approvedCaDto.getIssuerDistinguishedName());
        ca.setSubjectDistinguishedName(approvedCaDto.getSubjectDistinguishedName());
        ca.setOcspResponse(CertificateAuthorityOcspResponseMapping.map(approvedCaDto.getOcspResponse())
                .orElse(null));
        ca.setPath(String.join(":", approvedCaDto.getSubjectDnPath()));
        ca.setTopCa(approvedCaDto.isTopCa());
        return ca;
    }

    /**
     * convert a group of ApprovedCaDtos into a list of CertificateAuthorities
     * @param approvedCaDtos
     * @return
     */
    public Set<CertificateAuthority> convert(Iterable<ApprovedCaDto> approvedCaDtos) {
        return Streams.stream(approvedCaDtos)
                .map(this::convert)
                .collect(Collectors.toSet());
    }
}

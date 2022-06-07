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
package org.niis.xroad.centralserver.restapi.openapi;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.centralserver.openapi.CertificationServicesApi;
import org.niis.xroad.centralserver.openapi.model.ApprovedCertificationService;
import org.niis.xroad.centralserver.openapi.model.CertificateAuthority;
import org.niis.xroad.centralserver.openapi.model.CertificationServiceSettings;
import org.niis.xroad.centralserver.openapi.model.OcspResponder;
import org.niis.xroad.centralserver.restapi.service.CertificationServicesService;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@RequiredArgsConstructor
public class CertificationServicesController implements CertificationServicesApi {

    private final CertificationServicesService certificationServicesService;

    @Override
    public ResponseEntity<ApprovedCertificationService> addCertificationService(
            MultipartFile certificate, String certificateProfileInfo,
            Boolean tlsAuth) {
        throw new NotImplementedException("addCertificationService not implemented yet");
    }

    @Override
    public ResponseEntity<CertificateAuthority> addCertificationServiceIntermediateCa(
            String id, MultipartFile certificate) {
        throw new NotImplementedException("addCertificationServiceIntermediateCa not implemented yet");
    }

    @Override
    public ResponseEntity<OcspResponder> addCertificationServiceOcspResponder(
            String id, String url, MultipartFile certificate) {
        throw new NotImplementedException("addCertificationServiceOcspResponder not implemented yet");
    }

    @Override
    public ResponseEntity<Void> deleteCertificationService(String id) {
        throw new NotImplementedException("deleteCertificationService not implemented yet");
    }

    @Override
    public ResponseEntity<ApprovedCertificationService> getCertificationService(String id) {
        throw new NotImplementedException("getCertificationService not implemented yet");
    }

    @Override
    public ResponseEntity<Set<CertificateAuthority>> getCertificationServiceIntermediateCas(String id) {
        throw new NotImplementedException("getCertificationServiceIntermediateCas not implemented yet");
    }

    @Override
    public ResponseEntity<Set<OcspResponder>> getCertificationServiceOcspResponders(String id) {
        throw new NotImplementedException("getCertificationServiceOcspResponders not implemented yet");
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CAS')")
    public ResponseEntity<Set<ApprovedCertificationService>> getCertificationServices() {
        return ResponseEntity.ok(certificationServicesService.getCertificationServices());
    }

    @Override
    public ResponseEntity<ApprovedCertificationService> updateCertificationService(
            String id, CertificationServiceSettings certificationServiceSettings) {
        throw new NotImplementedException("updateCertificationService not implemented yet");
    }
}

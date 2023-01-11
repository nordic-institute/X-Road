/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.rest.api.openapi;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.cs.admin.api.dto.ApprovedCertificationService;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.CertificationService;
import org.niis.xroad.cs.admin.api.dto.OcspResponderAddRequest;
import org.niis.xroad.cs.admin.api.service.CertificationServicesService;
import org.niis.xroad.cs.admin.rest.api.converter.ApprovedCertificationServiceDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.CertificateAuthorityDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.CertificateDetailsDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.OcspResponderDtoConverter;
import org.niis.xroad.cs.openapi.CertificationServicesApi;
import org.niis.xroad.cs.openapi.model.ApprovedCertificationServiceDto;
import org.niis.xroad.cs.openapi.model.ApprovedCertificationServiceListItemDto;
import org.niis.xroad.cs.openapi.model.CertificateAuthorityDto;
import org.niis.xroad.cs.openapi.model.CertificateDetailsDto;
import org.niis.xroad.cs.openapi.model.CertificationServiceSettingsDto;
import org.niis.xroad.cs.openapi.model.OcspResponderDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.util.MultipartFileUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Boolean.parseBoolean;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Controller
@PreAuthorize("denyAll")
@RequiredArgsConstructor
@RequestMapping(ControllerUtil.API_V1_PREFIX)
public class CertificationServicesController implements CertificationServicesApi {

    private final CertificationServicesService certificationServicesService;
    private final ApprovedCertificationServiceDtoConverter approvedCertificationServiceDtoConverter;
    private final OcspResponderDtoConverter ocspResponderDtoConverter;
    private final CertificateDetailsDtoConverter certificateDetailsDtoConverter;
    private final CertificateAuthorityDtoConverter certificateAuthorityDtoConverter;

    @Override
    @PreAuthorize("hasAuthority('ADD_APPROVED_CA')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_CERTIFICATION_SERVICE)
    public ResponseEntity<ApprovedCertificationServiceDto> addCertificationService(MultipartFile certificate,
                                                                                   String certificateProfileInfo,
                                                                                   String tlsAuth) {
        var isForTlsAuth = parseBoolean(tlsAuth);
        var approvedCa = new ApprovedCertificationService(MultipartFileUtils.readBytes(certificate), certificateProfileInfo, isForTlsAuth);

        CertificationService persistedApprovedCa = certificationServicesService.add(approvedCa);
        return status(CREATED).body(approvedCertificationServiceDtoConverter.convert(persistedApprovedCa));
    }

    @Override
    @AuditEventMethod(event = RestApiAuditEvent.ADD_CERTIFICATION_SERVICE_INTERMEDIATE_CA)
    @PreAuthorize("hasAuthority('ADD_APPROVED_CA')")
    public ResponseEntity<CertificateAuthorityDto> addCertificationServiceIntermediateCa(Integer id, MultipartFile certificate) {
        final CertificateAuthority certificateAuthority = certificationServicesService
                .addIntermediateCa(id, MultipartFileUtils.readBytes(certificate));
        return status(CREATED).body(certificateAuthorityDtoConverter.convert(certificateAuthority));
    }

    @Override
    @AuditEventMethod(event = RestApiAuditEvent.ADD_CERTIFICATION_SERVICE_OCSP_RESPONDER)
    @PreAuthorize("hasAuthority('ADD_APPROVED_CA')")
    public ResponseEntity<OcspResponderDto> addCertificationServiceOcspResponder(Integer caId, String url, MultipartFile certificate) {
        final var addRequest = new OcspResponderAddRequest();
        addRequest.setCaId(caId).setUrl(url).setCertificate(MultipartFileUtils.readBytes(certificate));

        var ocspResponder = certificationServicesService.addOcspResponder(addRequest);
        return status(CREATED).body(ocspResponderDtoConverter.toDto(ocspResponder));
    }

    @Override
    public ResponseEntity<Void> deleteCertificationService(String id) {
        throw new NotImplementedException("deleteCertificationService not implemented yet");
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CA_DETAILS')")
    public ResponseEntity<ApprovedCertificationServiceDto> getCertificationService(Integer id) {
        return ok(approvedCertificationServiceDtoConverter.convert(certificationServicesService.get(id)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CA_DETAILS')")
    public ResponseEntity<CertificateDetailsDto> getCertificationServiceCertificate(Integer id) {
        return ok(certificateDetailsDtoConverter.convert(certificationServicesService.getCertificateDetails(id)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CA_DETAILS')")
    public ResponseEntity<Set<CertificateAuthorityDto>> getCertificationServiceIntermediateCas(Integer id) {
        return ok(certificateAuthorityDtoConverter.convert(certificationServicesService.getIntermediateCas(id)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CA_DETAILS')")
    public ResponseEntity<Set<OcspResponderDto>> getCertificationServiceOcspResponders(Integer id) {
        return ok(certificationServicesService.getOcspResponders(id).stream()
                .map(ocspResponderDtoConverter::toDto)
                .collect(Collectors.toSet())
        );
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CAS')")
    public ResponseEntity<Set<ApprovedCertificationServiceListItemDto>> getCertificationServices() {
        return ok(approvedCertificationServiceDtoConverter.convertListItems(certificationServicesService.getCertificationServices()));
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_APPROVED_CA')")
    @AuditEventMethod(event = RestApiAuditEvent.EDIT_CERTIFICATION_SERVICE_SETTINGS)
    public ResponseEntity<ApprovedCertificationServiceDto> updateCertificationService(String id, CertificationServiceSettingsDto settings) {
        CertificationService approvedCa = new CertificationService()
                .setId(Integer.valueOf(id))
                .setCertificateProfileInfo(settings.getCertificateProfileInfo())
                .setTlsAuth(parseBoolean(settings.getTlsAuth()));

        return ok(approvedCertificationServiceDtoConverter.convert(certificationServicesService.update(approvedCa)));
    }
}
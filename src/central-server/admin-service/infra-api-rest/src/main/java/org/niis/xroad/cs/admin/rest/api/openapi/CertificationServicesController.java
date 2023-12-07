/*
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
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.service.FileVerifier;
import org.niis.xroad.restapi.util.MultipartFileUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.toList;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_CERTIFICATION_SERVICE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_CERTIFICATION_SERVICE_INTERMEDIATE_CA;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_CERTIFICATION_SERVICE_OCSP_RESPONDER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_CERTIFICATION_SERVICE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_CERTIFICATION_SERVICE_SETTINGS;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.noContent;
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
    private final FileVerifier fileVerifier;

    @Override
    @PreAuthorize("hasAuthority('ADD_APPROVED_CA')")
    @AuditEventMethod(event = ADD_CERTIFICATION_SERVICE)
    public ResponseEntity<ApprovedCertificationServiceDto> addCertificationService(MultipartFile certificate,
                                                                                   String certificateProfileInfo,
                                                                                   String tlsAuth) {
        var isForTlsAuth = parseBoolean(tlsAuth);
        byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
        fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
        var approvedCa = new ApprovedCertificationService(fileBytes, certificateProfileInfo, isForTlsAuth);

        CertificationService persistedApprovedCa = certificationServicesService.add(approvedCa);
        return status(CREATED).body(approvedCertificationServiceDtoConverter.convert(persistedApprovedCa));
    }

    @Override
    @AuditEventMethod(event = ADD_CERTIFICATION_SERVICE_INTERMEDIATE_CA)
    @PreAuthorize("hasAuthority('ADD_APPROVED_CA')")
    public ResponseEntity<CertificateAuthorityDto> addCertificationServiceIntermediateCa(Integer id, MultipartFile certificate) {
        byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
        fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
        final CertificateAuthority certificateAuthority = certificationServicesService
                .addIntermediateCa(id, fileBytes);
        return status(CREATED).body(certificateAuthorityDtoConverter.convert(certificateAuthority));
    }

    @Override
    @AuditEventMethod(event = ADD_CERTIFICATION_SERVICE_OCSP_RESPONDER)
    @PreAuthorize("hasAuthority('ADD_APPROVED_CA')")
    public ResponseEntity<OcspResponderDto> addCertificationServiceOcspResponder(Integer caId, String url, MultipartFile certificate) {
        final var addRequest = new OcspResponderAddRequest();
        addRequest
                .setCaId(caId)
                .setUrl(url);

        if (certificate != null && !certificate.isEmpty()) {
            byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
            fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
            addRequest.setCertificate(fileBytes);
        }

        var ocspResponder = certificationServicesService.addOcspResponder(addRequest);
        return status(CREATED).body(ocspResponderDtoConverter.toDto(ocspResponder));
    }

    @Override
    @AuditEventMethod(event = DELETE_CERTIFICATION_SERVICE)
    @PreAuthorize("hasAuthority('DELETE_APPROVED_CA')")
    public ResponseEntity<Void> deleteCertificationService(Integer id) {
        certificationServicesService.delete(id);
        return noContent().build();
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
    public ResponseEntity<List<CertificateAuthorityDto>> getCertificationServiceIntermediateCas(Integer id) {
        return ok(certificateAuthorityDtoConverter.convert(certificationServicesService.getIntermediateCas(id)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CA_DETAILS')")
    public ResponseEntity<List<OcspResponderDto>> getCertificationServiceOcspResponders(Integer id) {
        return ok(certificationServicesService.getOcspResponders(id).stream()
                .map(ocspResponderDtoConverter::toDto)
                .collect(toList())
        );
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CAS')")
    public ResponseEntity<List<ApprovedCertificationServiceListItemDto>> getCertificationServices() {
        return ok(approvedCertificationServiceDtoConverter.convertListItems(certificationServicesService.getCertificationServices()));
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_APPROVED_CA')")
    @AuditEventMethod(event = EDIT_CERTIFICATION_SERVICE_SETTINGS)
    public ResponseEntity<ApprovedCertificationServiceDto> updateCertificationService(Integer id,
                                                                                      CertificationServiceSettingsDto settings) {
        CertificationService approvedCa = new CertificationService()
                .setId(id)
                .setCertificateProfileInfo(settings.getCertificateProfileInfo())
                .setTlsAuth(parseBoolean(settings.getTlsAuth()));

        return ok(approvedCertificationServiceDtoConverter.convert(certificationServicesService.update(approvedCa)));
    }
}

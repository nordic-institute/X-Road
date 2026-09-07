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
import org.niis.xroad.cs.admin.api.dto.ApprovedDsTlsCertificationAuthority;
import org.niis.xroad.cs.admin.api.dto.DsTlsCertificationAuthority;
import org.niis.xroad.cs.admin.api.dto.DsTlsIntermediateCertificateAuthority;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificationAuthoritiesService;
import org.niis.xroad.cs.admin.rest.api.converter.CertificateDetailsDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.DsTlsCertificationAuthorityDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.DsTlsIntermediateCertificateAuthorityDtoConverter;
import org.niis.xroad.cs.openapi.DsTlsCertificationAuthoritiesApi;
import org.niis.xroad.cs.openapi.model.ApprovedDsTlsCertificationAuthorityDto;
import org.niis.xroad.cs.openapi.model.ApprovedDsTlsCertificationAuthorityListItemDto;
import org.niis.xroad.cs.openapi.model.CertificateDetailsDto;
import org.niis.xroad.cs.openapi.model.DsTlsCertificationAuthoritySettingsDto;
import org.niis.xroad.cs.openapi.model.DsTlsIntermediateCertificateAuthorityDto;
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

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_DS_TLS_CERTIFICATION_AUTHORITY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_DS_TLS_CERTIFICATION_AUTHORITY_INTERMEDIATE_CA;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_DS_TLS_CERTIFICATION_AUTHORITY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_DS_TLS_CERTIFICATION_AUTHORITY_SETTINGS;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Controller
@PreAuthorize("denyAll")
@RequiredArgsConstructor
@RequestMapping(ControllerUtil.API_V1_PREFIX)
public class DsTlsCertificationAuthoritiesController implements DsTlsCertificationAuthoritiesApi {

    private final DsTlsCertificationAuthoritiesService dsTlsCertificationAuthoritiesService;
    private final DsTlsCertificationAuthorityDtoConverter dsTlsCertificationAuthorityDtoConverter;
    private final DsTlsIntermediateCertificateAuthorityDtoConverter dsTlsIntermediateCertificateAuthorityDtoConverter;
    private final CertificateDetailsDtoConverter certificateDetailsDtoConverter;
    private final FileVerifier fileVerifier;

    @Override
    @PreAuthorize("hasAuthority('ADD_APPROVED_DS_TLS_CA')")
    @AuditEventMethod(event = ADD_DS_TLS_CERTIFICATION_AUTHORITY)
    public ResponseEntity<ApprovedDsTlsCertificationAuthorityDto> addDsTlsCertificationAuthority(MultipartFile certificate,
                                                                                                  String name,
                                                                                                  String acmeServerDirectoryUrl,
                                                                                                  String dsTlsCertificateProfileId) {
        byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
        fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
        var newCa = new ApprovedDsTlsCertificationAuthority(fileBytes, name, acmeServerDirectoryUrl, dsTlsCertificateProfileId);

        DsTlsCertificationAuthority persisted = dsTlsCertificationAuthoritiesService.add(newCa);
        return status(CREATED).body(dsTlsCertificationAuthorityDtoConverter.convert(persisted));
    }

    @Override
    @AuditEventMethod(event = ADD_DS_TLS_CERTIFICATION_AUTHORITY_INTERMEDIATE_CA)
    @PreAuthorize("hasAuthority('ADD_APPROVED_DS_TLS_CA')")
    public ResponseEntity<DsTlsIntermediateCertificateAuthorityDto> addDsTlsCertificationAuthorityIntermediateCa(
            Integer dsTlsCertificationAuthorityId, MultipartFile certificate) {
        byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
        fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
        final DsTlsIntermediateCertificateAuthority intermediateCa = dsTlsCertificationAuthoritiesService
                .addIntermediateCa(dsTlsCertificationAuthorityId, fileBytes);
        return status(CREATED).body(dsTlsIntermediateCertificateAuthorityDtoConverter.convert(intermediateCa));
    }

    @Override
    @AuditEventMethod(event = DELETE_DS_TLS_CERTIFICATION_AUTHORITY)
    @PreAuthorize("hasAuthority('DELETE_APPROVED_DS_TLS_CA')")
    public ResponseEntity<Void> deleteDsTlsCertificationAuthority(Integer dsTlsCertificationAuthorityId) {
        dsTlsCertificationAuthoritiesService.delete(dsTlsCertificationAuthorityId);
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_DS_TLS_CA_DETAILS')")
    public ResponseEntity<ApprovedDsTlsCertificationAuthorityDto> getDsTlsCertificationAuthority(Integer dsTlsCertificationAuthorityId) {
        return ok(dsTlsCertificationAuthorityDtoConverter.convert(dsTlsCertificationAuthoritiesService.get(dsTlsCertificationAuthorityId)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_DS_TLS_CA_DETAILS')")
    public ResponseEntity<CertificateDetailsDto> getDsTlsCertificationAuthorityCertificate(Integer dsTlsCertificationAuthorityId) {
        return ok(certificateDetailsDtoConverter.convert(
                dsTlsCertificationAuthoritiesService.getCertificateDetails(dsTlsCertificationAuthorityId)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_DS_TLS_CA_DETAILS')")
    public ResponseEntity<List<DsTlsIntermediateCertificateAuthorityDto>> getDsTlsCertificationAuthorityIntermediateCas(
            Integer dsTlsCertificationAuthorityId) {
        return ok(dsTlsIntermediateCertificateAuthorityDtoConverter.convert(
                dsTlsCertificationAuthoritiesService.getIntermediateCas(dsTlsCertificationAuthorityId)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_DS_TLS_CAS')")
    public ResponseEntity<List<ApprovedDsTlsCertificationAuthorityListItemDto>> getDsTlsCertificationAuthorities() {
        return ok(dsTlsCertificationAuthorityDtoConverter.convertListItems(
                dsTlsCertificationAuthoritiesService.getDsTlsCertificationAuthorities()));
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_APPROVED_DS_TLS_CA')")
    @AuditEventMethod(event = EDIT_DS_TLS_CERTIFICATION_AUTHORITY_SETTINGS)
    public ResponseEntity<ApprovedDsTlsCertificationAuthorityDto> updateDsTlsCertificationAuthority(
            Integer dsTlsCertificationAuthorityId, DsTlsCertificationAuthoritySettingsDto settings) {
        DsTlsCertificationAuthority updateRequest = new DsTlsCertificationAuthority()
                .setId(dsTlsCertificationAuthorityId)
                .setName(settings.getName())
                .setAcmeServerDirectoryUrl(settings.getAcmeServerDirectoryUrl())
                .setDsTlsCertificateProfileId(settings.getDsTlsCertificateProfileId());

        return ok(dsTlsCertificationAuthorityDtoConverter.convert(dsTlsCertificationAuthoritiesService.update(updateRequest)));
    }
}

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
import org.niis.xroad.cs.admin.api.dto.AddDsTlsCaRequest;
import org.niis.xroad.cs.admin.api.dto.DsTlsCa;
import org.niis.xroad.cs.admin.api.service.DsTlsCasService;
import org.niis.xroad.cs.admin.rest.api.converter.CertificateDetailsDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.DsTlsCaDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.DsTlsCaIntermediateCaDtoConverter;
import org.niis.xroad.cs.openapi.DsTlsCasApi;
import org.niis.xroad.cs.openapi.model.ApprovedDsTlsCaDto;
import org.niis.xroad.cs.openapi.model.ApprovedDsTlsCaListItemDto;
import org.niis.xroad.cs.openapi.model.CertificateDetailsDto;
import org.niis.xroad.cs.openapi.model.DsTlsCaIntermediateCaDto;
import org.niis.xroad.cs.openapi.model.DsTlsCaSettingsDto;
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

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_DS_TLS_CA;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_DS_TLS_CA_INTERMEDIATE_CA;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_DS_TLS_CA;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_DS_TLS_CA_INTERMEDIATE_CA;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_DS_TLS_CA_SETTINGS;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Controller
@PreAuthorize("denyAll")
@RequiredArgsConstructor
@RequestMapping(ControllerUtil.API_V1_PREFIX)
public class DsTlsCasController implements DsTlsCasApi {

    private final DsTlsCasService dsTlsCasService;
    private final DsTlsCaDtoConverter dsTlsCaDtoConverter;
    private final DsTlsCaIntermediateCaDtoConverter dsTlsCaIntermediateCaDtoConverter;
    private final CertificateDetailsDtoConverter certificateDetailsDtoConverter;
    private final FileVerifier fileVerifier;

    @Override
    @PreAuthorize("hasAuthority('ADD_DS_TLS_CA')")
    @AuditEventMethod(event = ADD_DS_TLS_CA)
    public ResponseEntity<ApprovedDsTlsCaDto> addDsTlsCa(MultipartFile certificate,
                                                          String acmeServerDirectoryUrl,
                                                          String dsTlsCertificateProfileId) {
        byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
        fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
        var request = new AddDsTlsCaRequest(fileBytes, acmeServerDirectoryUrl, dsTlsCertificateProfileId);

        var persistedDsTlsCa = dsTlsCasService.add(request);
        return status(CREATED).body(dsTlsCaDtoConverter.convert(persistedDsTlsCa));
    }

    @Override
    @AuditEventMethod(event = ADD_DS_TLS_CA_INTERMEDIATE_CA)
    @PreAuthorize("hasAuthority('ADD_DS_TLS_CA')")
    public ResponseEntity<DsTlsCaIntermediateCaDto> addDsTlsCaIntermediateCa(Integer dsTlsCaId, MultipartFile certificate) {
        byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
        fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
        var intermediateCa = dsTlsCasService.addIntermediateCa(dsTlsCaId, fileBytes);
        return status(CREATED).body(dsTlsCaIntermediateCaDtoConverter.convert(intermediateCa));
    }

    @Override
    @AuditEventMethod(event = DELETE_DS_TLS_CA)
    @PreAuthorize("hasAuthority('DELETE_DS_TLS_CA')")
    public ResponseEntity<Void> deleteDsTlsCa(Integer dsTlsCaId) {
        dsTlsCasService.delete(dsTlsCaId);
        return noContent().build();
    }

    @Override
    @AuditEventMethod(event = DELETE_DS_TLS_CA_INTERMEDIATE_CA)
    @PreAuthorize("hasAuthority('DELETE_DS_TLS_CA')")
    public ResponseEntity<Void> deleteDsTlsCaIntermediateCa(Integer intermediateCaId) {
        dsTlsCasService.deleteIntermediateCa(intermediateCaId);
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CA_DETAILS')")
    public ResponseEntity<ApprovedDsTlsCaDto> getDsTlsCa(Integer dsTlsCaId) {
        return ok(dsTlsCaDtoConverter.convert(dsTlsCasService.get(dsTlsCaId)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CA_DETAILS')")
    public ResponseEntity<CertificateDetailsDto> getDsTlsCaCertificate(Integer dsTlsCaId) {
        return ok(certificateDetailsDtoConverter.convert(dsTlsCasService.getCertificateDetails(dsTlsCaId)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CA_DETAILS')")
    public ResponseEntity<DsTlsCaIntermediateCaDto> getDsTlsCaIntermediateCa(Integer intermediateCaId) {
        return ok(dsTlsCaIntermediateCaDtoConverter.convert(dsTlsCasService.getIntermediateCa(intermediateCaId)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CA_DETAILS')")
    public ResponseEntity<List<DsTlsCaIntermediateCaDto>> getDsTlsCaIntermediateCas(Integer dsTlsCaId) {
        return ok(dsTlsCaIntermediateCaDtoConverter.convert(dsTlsCasService.getIntermediateCas(dsTlsCaId)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CAS')")
    public ResponseEntity<List<ApprovedDsTlsCaListItemDto>> getDsTlsCas() {
        return ok(dsTlsCaDtoConverter.convertListItems(dsTlsCasService.getDsTlsCas()));
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_DS_TLS_CA')")
    @AuditEventMethod(event = EDIT_DS_TLS_CA_SETTINGS)
    public ResponseEntity<ApprovedDsTlsCaDto> updateDsTlsCa(Integer dsTlsCaId, DsTlsCaSettingsDto dsTlsCaSettingsDto) {
        var dsTlsCa = new DsTlsCa()
                .setId(dsTlsCaId)
                .setAcmeServerDirectoryUrl(dsTlsCaSettingsDto.getAcmeServerDirectoryUrl())
                .setDsTlsCertificateProfileId(dsTlsCaSettingsDto.getDsTlsCertificateProfileId());

        return ok(dsTlsCaDtoConverter.convert(dsTlsCasService.update(dsTlsCa)));
    }
}

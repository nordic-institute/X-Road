/*
 * The MIT License
 *
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
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.dto.OcspResponderAddRequest;
import org.niis.xroad.cs.admin.api.dto.OcspResponderRequest;
import org.niis.xroad.cs.admin.api.service.IntermediateCasService;
import org.niis.xroad.cs.admin.rest.api.converter.CertificateAuthorityDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.OcspResponderDtoConverter;
import org.niis.xroad.cs.openapi.IntermediateCasApi;
import org.niis.xroad.cs.openapi.model.CertificateAuthorityDto;
import org.niis.xroad.cs.openapi.model.OcspResponderDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.service.FileVerifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_INTERMEDIATE_CA_OCSP_RESPONDER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_INTERMEDIATE_CA;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_OCSP_RESPONDER;
import static org.niis.xroad.restapi.util.MultipartFileUtils.readBytes;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class IntermediateCasController implements IntermediateCasApi {

    private final IntermediateCasService intermediateCasService;

    private final CertificateAuthorityDtoConverter certificateAuthorityDtoConverter;
    private final OcspResponderDtoConverter ocspResponderDtoConverter;
    private final FileVerifier fileVerifier;

    @Override
    @PreAuthorize("hasAuthority('ADD_APPROVED_CA')")
    @AuditEventMethod(event = ADD_INTERMEDIATE_CA_OCSP_RESPONDER)
    public ResponseEntity<OcspResponderDto> addIntermediateCaOcspResponder(Integer id, String url, MultipartFile certificate) {
        final OcspResponderRequest ocspResponderRequest = new OcspResponderAddRequest()
                .setUrl(url);

        if (certificate != null && !certificate.isEmpty()) {
            byte[] fileBytes = readBytes(certificate);
            fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
            ocspResponderRequest.setCertificate(fileBytes);
        }
        final OcspResponder ocspResponder = intermediateCasService.addOcspResponder(id, ocspResponderRequest);

        return status(CREATED).body(ocspResponderDtoConverter.toDto(ocspResponder));
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_APPROVED_CA')")
    @AuditEventMethod(event = DELETE_OCSP_RESPONDER)
    public ResponseEntity<Void> deleteIntermediateCaOcspResponder(Integer intermediateCaId, Integer ocspResponderId) {
        intermediateCasService.deleteOcspResponder(intermediateCaId, ocspResponderId);
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_APPROVED_CA')")
    @AuditEventMethod(event = DELETE_INTERMEDIATE_CA)
    public ResponseEntity<Void> deleteIntermediateCa(Integer id) {
        intermediateCasService.delete(id);
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CA_DETAILS')")
    public ResponseEntity<CertificateAuthorityDto> getIntermediateCa(Integer id) {
        return ok(certificateAuthorityDtoConverter.convert(intermediateCasService.get(id)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CA_DETAILS')")
    public ResponseEntity<List<OcspResponderDto>> getIntermediateCaOcspResponders(Integer id) {
        return ok(intermediateCasService.getOcspResponders(id).stream()
                .map(ocspResponderDtoConverter::toDto)
                .toList());
    }
}

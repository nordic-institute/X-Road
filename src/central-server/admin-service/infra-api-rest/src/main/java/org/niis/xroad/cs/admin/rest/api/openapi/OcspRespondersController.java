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
import org.niis.xroad.cs.admin.api.dto.OcspResponderRequest;
import org.niis.xroad.cs.admin.api.service.OcspRespondersService;
import org.niis.xroad.cs.admin.rest.api.converter.CertificateDetailsDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.OcspResponderDtoConverter;
import org.niis.xroad.cs.openapi.OcspRespondersApi;
import org.niis.xroad.cs.openapi.model.CertificateDetailsDto;
import org.niis.xroad.cs.openapi.model.OcspResponderDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.service.FileVerifier;
import org.niis.xroad.restapi.util.MultipartFileUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@Controller
@PreAuthorize("denyAll")
@RequiredArgsConstructor
@RequestMapping(ControllerUtil.API_V1_PREFIX)
public class OcspRespondersController implements OcspRespondersApi {

    private final OcspRespondersService ocspRespondersService;
    private final CertificateDetailsDtoConverter certificateDetailsDtoConverter;
    private final OcspResponderDtoConverter ocspResponderDtoConverter;
    private final FileVerifier fileVerifier;


    @Override
    @PreAuthorize("hasAuthority('EDIT_APPROVED_CA')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_OCSP_RESPONDER)
    public ResponseEntity<Void> deleteOcspResponder(Integer id) {
        ocspRespondersService.delete(id);
        return noContent().build();
    }

    @Override
    public ResponseEntity<OcspResponderDto> getOcspResponder(Integer id) {
        throw new NotImplementedException("getOcspResponder not implemented yet");
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_APPROVED_CA')")
    @AuditEventMethod(event = RestApiAuditEvent.EDIT_OCSP_RESPONDER)
    public ResponseEntity<OcspResponderDto> updateOcspResponder(Integer id, String url, MultipartFile certificate) {
        final OcspResponderRequest updateRequest = new OcspResponderRequest()
                .setId(id)
                .setUrl(url);
        if (certificate != null) {
            byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
            fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
            updateRequest.setCertificate(fileBytes);
        }
        return ok(ocspResponderDtoConverter.toDto(ocspRespondersService.update(updateRequest)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CA_DETAILS')")
    public ResponseEntity<CertificateDetailsDto> getOcspRespondersCertificate(Integer id) {
        return ok(certificateDetailsDtoConverter.convert(ocspRespondersService.getOcspResponderCertificateDetails(id)));
    }
}

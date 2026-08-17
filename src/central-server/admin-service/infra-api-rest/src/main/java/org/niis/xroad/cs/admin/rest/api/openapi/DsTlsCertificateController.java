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
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificateService;
import org.niis.xroad.cs.admin.rest.api.converter.CertificateDetailsDtoConverter;
import org.niis.xroad.cs.openapi.DsTlsCertificateApi;
import org.niis.xroad.cs.openapi.model.CertificateDetailsDto;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.util.MultipartFileUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_CONFIGURED;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.UPLOAD_DS_TLS_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_FILE_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.KEY_FILE_NAME;
import static org.springframework.http.ResponseEntity.ok;

@Controller
@PreAuthorize("denyAll")
@RequiredArgsConstructor
@RequestMapping(ControllerUtil.API_V1_PREFIX)
public class DsTlsCertificateController implements DsTlsCertificateApi {
    private static final String DS_TLS_CERTIFICATE_TAR_FILENAME = "ds-tls-certificate.tar.gz";

    private final DsTlsCertificateService dsTlsCertificateService;
    private final CertificateDetailsDtoConverter certificateDetailsDtoConverter;
    private final AuditDataHelper auditDataHelper;

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CERT')")
    public ResponseEntity<CertificateDetailsDto> getDsTlsCertificate() {
        CertificateDetails certificateDetails = dsTlsCertificateService.getTlsCertificateDetails();
        if (certificateDetails == null) {
            throw new NotFoundException(DS_TLS_CERTIFICATE_NOT_CONFIGURED.build());
        }
        return ok(certificateDetailsDtoConverter.convert(certificateDetails));
    }

    @Override
    @PreAuthorize("hasAuthority('DOWNLOAD_DS_TLS_CERT')")
    public ResponseEntity<Resource> downloadDsTlsCertificate() {
        byte[] certificateTar = dsTlsCertificateService.getTlsCertificateTar();
        return ControllerUtil.createAttachmentResourceResponse(certificateTar, DS_TLS_CERTIFICATE_TAR_FILENAME);
    }

    @Override
    @PreAuthorize("hasAuthority('UPLOAD_DS_TLS_CERT')")
    @AuditEventMethod(event = UPLOAD_DS_TLS_CERT)
    public ResponseEntity<CertificateDetailsDto> uploadDsTlsCertificate(MultipartFile key, MultipartFile certificate) {
        auditDataHelper.put(KEY_FILE_NAME, key.getOriginalFilename());
        auditDataHelper.put(CERT_FILE_NAME, certificate.getOriginalFilename());

        byte[] keyBytes = MultipartFileUtils.readBytes(key);
        byte[] certificateBytes = MultipartFileUtils.readBytes(certificate);

        CertificateDetails certificateDetails = dsTlsCertificateService.uploadCertificate(keyBytes, certificateBytes);
        return ok(certificateDetailsDtoConverter.convert(certificateDetails));
    }
}

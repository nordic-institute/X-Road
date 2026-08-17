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
package org.niis.xroad.securityserver.restapi.openapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.util.ResourceUtils;
import org.niis.xroad.securityserver.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetailsDto;
import org.niis.xroad.securityserver.restapi.service.DsTlsCertificateService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.security.cert.X509Certificate;

import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_CONFIGURED;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class DsTlsCertificateApiController implements DsTlsCertificateApi {

    private static final String DS_TLS_CERTIFICATE_TAR_FILENAME = "ds-tls-certs.tar.gz";

    private final DsTlsCertificateService dsTlsCertificateService;
    private final CertificateDetailsConverter certificateDetailsConverter;
    private final AuditDataHelper auditDataHelper;

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CERT')")
    public ResponseEntity<CertificateDetailsDto> getDsTlsCertificate() {
        X509Certificate certificate = dsTlsCertificateService.getDsTlsCertificate()
                .orElseThrow(() -> new NotFoundException(DS_TLS_CERTIFICATE_NOT_CONFIGURED.build()));
        return new ResponseEntity<>(certificateDetailsConverter.convert(certificate), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DOWNLOAD_DS_TLS_CERT')")
    public ResponseEntity<Resource> downloadDsTlsCertificate() {
        byte[] certificateTar = dsTlsCertificateService.exportDsTlsCertificate();
        return ControllerUtil.createAttachmentResourceResponse(certificateTar, DS_TLS_CERTIFICATE_TAR_FILENAME);
    }

    @Override
    @PreAuthorize("hasAuthority('UPLOAD_DS_TLS_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.UPLOAD_DS_TLS_CERT)
    public ResponseEntity<CertificateDetailsDto> uploadDsTlsCertificate(MultipartFile key, MultipartFile certificate) {
        auditDataHelper.put(RestApiAuditProperty.KEY_FILE_NAME, key.getOriginalFilename());
        auditDataHelper.put(RestApiAuditProperty.CERT_FILE_NAME, certificate.getOriginalFilename());

        byte[] keyBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(key);
        byte[] certificateBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(certificate);

        X509Certificate x509Certificate = dsTlsCertificateService.importDsTlsCertificate(keyBytes, certificateBytes);
        return new ResponseEntity<>(certificateDetailsConverter.convert(x509Certificate), HttpStatus.OK);
    }
}

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
package org.niis.xroad.restapi.openapi;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.converter.DsTlsCertificateDetailsConverter;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.DistinguishedName;
import org.niis.xroad.restapi.openapi.model.DsTlsCertificateStatus;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.niis.xroad.restapi.util.MultipartFileUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.security.cert.X509Certificate;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.GENERATE_DS_TLS_CSR;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.GENERATE_DS_TLS_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.UPLOAD_DS_TLS_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_FILE_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SUBJECT_NAME;
import static org.springframework.http.ResponseEntity.ok;

/**
 * Serves the DataSpace TLS certificate slot for both Security Server and Central Server admin services.
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class DsTlsCertificateController implements DsTlsCertificateApi {

    private static final String CSR_FILENAME = "ds-tls-cert-request.p10";
    private static final String CERTIFICATE_TAR_FILENAME = "ds-tls-certificate.tar.gz";

    private final DsTlsCertificateService dsTlsCertificateService;
    private final DsTlsCertificateDetailsConverter certificateDetailsConverter;
    private final AuditDataHelper auditDataHelper;

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CERT')")
    public ResponseEntity<DsTlsCertificateStatus> getDsTlsCertificateStatus() {
        var status = dsTlsCertificateService.getStatus();
        var response = new DsTlsCertificateStatus(status.keyGenerated())
                .certificate(status.certificateAcquired() ? certificateDetailsConverter.convert(status.certificate()) : null);
        return ok(response);
    }

    @Override
    @PreAuthorize("hasAuthority('GENERATE_DS_TLS_KEY')")
    @AuditEventMethod(event = GENERATE_DS_TLS_KEY)
    public ResponseEntity<Void> generateDsTlsKey() {
        dsTlsCertificateService.generateKey();
        return ControllerUtil.createCreatedResponse(DsTlsCertificateApi.PATH_GET_DS_TLS_CERTIFICATE_STATUS, null);
    }

    @Override
    @PreAuthorize("hasAuthority('GENERATE_DS_TLS_CSR')")
    @AuditEventMethod(event = GENERATE_DS_TLS_CSR)
    public ResponseEntity<Resource> generateDsTlsCsr(DistinguishedName distinguishedName) {
        auditDataHelper.put(SUBJECT_NAME, distinguishedName.getName());
        byte[] csrBytes = dsTlsCertificateService.generateCsr(distinguishedName.getName());
        return ControllerUtil.createAttachmentResourceResponse(csrBytes, CSR_FILENAME);
    }

    @Override
    @PreAuthorize("hasAuthority('DOWNLOAD_DS_TLS_CERT')")
    public ResponseEntity<Resource> downloadDsTlsCertificate() {
        byte[] certificateTar = dsTlsCertificateService.downloadCertificateTar();
        return ControllerUtil.createAttachmentResourceResponse(certificateTar, CERTIFICATE_TAR_FILENAME);
    }

    @Override
    @PreAuthorize("hasAuthority('UPLOAD_DS_TLS_CERT')")
    @AuditEventMethod(event = UPLOAD_DS_TLS_CERT)
    public ResponseEntity<CertificateDetails> uploadDsTlsCertificate(MultipartFile certificate) {
        auditDataHelper.put(CERT_FILE_NAME, certificate.getOriginalFilename());
        byte[] certificateBytes = MultipartFileUtils.readBytes(certificate);
        X509Certificate stored = dsTlsCertificateService.uploadCertificate(certificateBytes);
        return ok(certificateDetailsConverter.convert(stored));
    }
}

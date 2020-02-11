/**
 * The MIT License
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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.restapi.converter.VersionConverter;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.Version;
import org.niis.xroad.restapi.service.InternalTlsCertificateService;
import org.niis.xroad.restapi.service.VersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.cert.X509Certificate;

/**
 * system api controller
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class SystemApiController implements SystemApi {

    private final InternalTlsCertificateService internalTlsCertificateService;
    private final CertificateDetailsConverter certificateDetailsConverter;
    private final VersionService versionService;
    private final VersionConverter versionConverter;

    /**
     * Constructor
     */
    @Autowired
    public SystemApiController(InternalTlsCertificateService internalTlsCertificateService,
            CertificateDetailsConverter certificateDetailsConverter,
            VersionService versionService,
            VersionConverter versionConverter) {
        this.internalTlsCertificateService = internalTlsCertificateService;
        this.certificateDetailsConverter = certificateDetailsConverter;
        this.versionService = versionService;
        this.versionConverter  = versionConverter;
    }

    @Override
    @PreAuthorize("hasAuthority('EXPORT_PROXY_INTERNAL_CERT')")
    public ResponseEntity<Resource> downloadSystemCertificate() {
        String filename = "certs.tar.gz";
        byte[] certificateTar = internalTlsCertificateService.exportInternalTlsCertificate();
        return ApiUtil.createAttachmentResourceResponse(certificateTar, filename);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('VIEW_PROXY_INTERNAL_CERT', 'VIEW_INTERNAL_SSL_CERT')")
    public ResponseEntity<CertificateDetails> getSystemCertificate() {
        X509Certificate x509Certificate = internalTlsCertificateService.getInternalTlsCertificate();
        CertificateDetails certificate = certificateDetailsConverter.convert(x509Certificate);
        return new ResponseEntity<>(certificate, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_VERSION')")
    public ResponseEntity<Version> systemVersion() {
        String softwareVersion = versionService.getVersion();
        Version version = versionConverter.convert(softwareVersion);
        return new ResponseEntity<>(version, HttpStatus.OK);
    }

}

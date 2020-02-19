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

import ee.ria.xroad.common.conf.serverconf.model.TspType;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.restapi.converter.TimestampingServiceConverter;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
import org.niis.xroad.restapi.service.InternalTlsCertificateService;
import org.niis.xroad.restapi.service.SystemService;
import org.niis.xroad.restapi.service.TimestampingServiceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * system api controller
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class SystemApiController implements SystemApi {
    public static final String INTERNAL_KEY_CERT_INTERRUPTED = "internal_key_cert_interrupted";

    private final InternalTlsCertificateService internalTlsCertificateService;
    private final CertificateDetailsConverter certificateDetailsConverter;
    private final TimestampingServiceConverter timestampingServiceConverter;
    private final SystemService systemService;

    /**
     * Constructor
     */
    @Autowired
    public SystemApiController(InternalTlsCertificateService internalTlsCertificateService,
            CertificateDetailsConverter certificateDetailsConverter, SystemService systemService,
                               TimestampingServiceConverter timestampingServiceConverter) {
        this.internalTlsCertificateService = internalTlsCertificateService;
        this.certificateDetailsConverter = certificateDetailsConverter;
        this.systemService = systemService;
        this.timestampingServiceConverter = timestampingServiceConverter;
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
    @PreAuthorize("hasAuthority('GENERATE_INTERNAL_SSL')")
    public ResponseEntity<Void> generateSystemTlsKeyAndCertificate() {
        try {
            internalTlsCertificateService.generateInternalTlsKeyAndCertificate();
        } catch (InterruptedException e) {
            throw new InternalServerErrorException(new ErrorDeviation(INTERNAL_KEY_CERT_INTERRUPTED));
        }
        return ApiUtil.createCreatedResponse("/api/system/certificate", null);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_TSPS')")
    public ResponseEntity<List<TimestampingService>> getConfiguredTimestampingServices() {
        List<TimestampingService> timestampingServices;
        List<TspType> tsps = systemService.getConfiguredTimestampingServices();
        timestampingServices = timestampingServiceConverter.convert(tsps);

        return new ResponseEntity<>(timestampingServices, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_TSP')")
    public ResponseEntity<TimestampingService> addConfiguredTimestampingService(
            TimestampingService timestampingServiceToAdd) {
        try {
            systemService.addConfiguredTimestampingService(timestampingServiceToAdd);
        } catch (SystemService.DuplicateConfiguredTimestampingServiceException e) {
            throw new ConflictException(e);
        } catch (TimestampingServiceNotFoundException e) {
            throw new BadRequestException(e);
        }
        return new ResponseEntity<>(timestampingServiceToAdd, HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_TSP')")
    public ResponseEntity<Void> deleteConfiguredTimestampingService(TimestampingService timestampingService) {
        try {
            systemService.deleteConfiguredTimestampingService(timestampingService);
        } catch (TimestampingServiceNotFoundException e) {
            throw new BadRequestException(e);
        }

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

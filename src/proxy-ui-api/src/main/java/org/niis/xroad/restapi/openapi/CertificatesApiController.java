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

import ee.ria.xroad.common.conf.serverconf.model.CertificateType;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.niis.xroad.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.service.CertificateAlreadyExistsException;
import org.niis.xroad.restapi.service.ClientNotFoundException;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.service.KeyNotFoundException;
import org.niis.xroad.restapi.service.TokenCertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;

/**
 * certificates api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class CertificatesApiController implements CertificatesApi {
    public static final String ERROR_INVALID_CERT_UPLOAD = "invalid_cert_upload";
    public static final String ERROR_INVALID_CERT = "invalid_cert";

    private final TokenCertificateService tokenCertificateService;
    private final CertificateDetailsConverter certificateDetailsConverter;

    @Autowired
    public CertificatesApiController(TokenCertificateService tokenCertificateService,
            CertificateDetailsConverter certificateDetailsConverter) {
        this.tokenCertificateService = tokenCertificateService;
        this.certificateDetailsConverter = certificateDetailsConverter;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('IMPORT_AUTH_CERT', 'IMPORT_SIGN_CERT')")
    public ResponseEntity<CertificateDetails> addCertificate(Resource certificateResource) {
        byte[] certificateBytes;
        try (InputStream is = certificateResource.getInputStream()) {
            certificateBytes = IOUtils.toByteArray(is);
        } catch (IOException ex) {
            throw new BadRequestException("cannot read certificate data", ex,
                    new ErrorDeviation(ERROR_INVALID_CERT_UPLOAD));
        }
        CertificateType certificate = null;
        try {
            certificate = tokenCertificateService.addCertificate(certificateBytes);
        } catch (GlobalConfService.GlobalConfOutdatedException | ClientNotFoundException | KeyNotFoundException
                | TokenCertificateService.WrongCertificateUsageException
                | TokenCertificateService.CsrNotFoundException
                | TokenCertificateService.InvalidCertificateException e) {
            throw new BadRequestException(e);
        } catch (CertificateAlreadyExistsException e) {
            throw new ConflictException(e);
        }
        CertificateDetails certificateDetails = certificateDetailsConverter.convert(certificate);
        return ApiUtil.createCreatedResponse("/api/certificates/{hash}", certificateDetails,
                certificateDetails.getHash());
    }
}

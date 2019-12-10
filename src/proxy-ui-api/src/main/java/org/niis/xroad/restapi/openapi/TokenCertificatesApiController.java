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
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.SecurityServerAddress;
import org.niis.xroad.restapi.service.CertificateAlreadyExistsException;
import org.niis.xroad.restapi.service.CertificateNotFoundException;
import org.niis.xroad.restapi.service.ClientNotFoundException;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.service.KeyNotFoundException;
import org.niis.xroad.restapi.service.ManagementRequestService;
import org.niis.xroad.restapi.service.ServerConfService;
import org.niis.xroad.restapi.service.TokenCertificateService;
import org.niis.xroad.restapi.util.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * certificates api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class TokenCertificatesApiController implements TokenCertificatesApi {

    private final TokenCertificateService tokenCertificateService;
    private final CertificateDetailsConverter certificateDetailsConverter;

    @Autowired
    public TokenCertificatesApiController(TokenCertificateService tokenCertificateService,
            CertificateDetailsConverter certificateDetailsConverter) {
        this.tokenCertificateService = tokenCertificateService;
        this.certificateDetailsConverter = certificateDetailsConverter;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('IMPORT_AUTH_CERT', 'IMPORT_SIGN_CERT')")
    public ResponseEntity<CertificateDetails> importCertificate(Resource certificateResource) {
        byte[] certificateBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(certificateResource);
        CertificateType certificate = null;
        try {
            certificate = tokenCertificateService.importCertificate(certificateBytes);
        } catch (GlobalConfService.GlobalConfOutdatedException | ClientNotFoundException | KeyNotFoundException
                | TokenCertificateService.WrongCertificateUsageException
                | TokenCertificateService.InvalidCertificateException e) {
            throw new BadRequestException(e);
        } catch (CertificateAlreadyExistsException | TokenCertificateService.CsrNotFoundException e) {
            throw new ConflictException(e);
        }
        CertificateDetails certificateDetails = certificateDetailsConverter.convert(certificate);
        return ApiUtil.createCreatedResponse("/api/token-certificates/{hash}", certificateDetails,
                certificateDetails.getHash());
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CERT')")
    public ResponseEntity<CertificateDetails> getCertificate(String hash) {
        CertificateInfo certificateInfo;
        try {
            certificateInfo = tokenCertificateService.getCertificateInfo(hash);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }

        CertificateDetails certificateDetails = certificateDetailsConverter.convert(certificateInfo);
        return new ResponseEntity<>(certificateDetails, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('IMPORT_AUTH_CERT', 'IMPORT_SIGN_CERT')")
    public ResponseEntity<CertificateDetails> importExistingCertificate(String hash) {
        CertificateType certificate = null;
        try {
            certificate = tokenCertificateService.importExistingCertificate(hash);
        } catch (GlobalConfService.GlobalConfOutdatedException | ClientNotFoundException | KeyNotFoundException
                | TokenCertificateService.WrongCertificateUsageException
                | TokenCertificateService.InvalidCertificateException e) {
            throw new BadRequestException(e);
        } catch (CertificateAlreadyExistsException | TokenCertificateService.CsrNotFoundException e) {
            throw new ConflictException(e);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        CertificateDetails certificateDetails = certificateDetailsConverter.convert(certificate);
        return ApiUtil.createCreatedResponse("/api/token-certificates/{hash}", certificateDetails,
                certificateDetails.getHash());
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_AUTH_CERT_REG_REQ')")
    public ResponseEntity<Void> registerCertificate(String hash, SecurityServerAddress securityServerAddress) {
        try {
            tokenCertificateService.registerAuthCert(hash, securityServerAddress.getAddress());
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServerConfService.MalformedServerConfException | ManagementRequestService.ManagementRequestException
                | GlobalConfService.GlobalConfOutdatedException e) {
            throw new BadRequestException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_AUTH_CERT_DEL_REQ')")
    public ResponseEntity<Void> unregisterCertificate(String hash) {
        try {
            tokenCertificateService.unregisterAuthCert(hash);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServerConfService.MalformedServerConfException | ManagementRequestService.ManagementRequestException
                | GlobalConfService.GlobalConfOutdatedException e) {
            throw new BadRequestException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

/**
 * The MIT License
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

import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.InternalServerErrorException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.util.ResourceUtils;
import org.niis.xroad.restapi.util.SecurityHelper;
import org.niis.xroad.securityserver.restapi.converter.PossibleActionConverter;
import org.niis.xroad.securityserver.restapi.converter.TokenCertificateConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.PossibleAction;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerAddress;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenCertificate;
import org.niis.xroad.securityserver.restapi.service.ActionNotPossibleException;
import org.niis.xroad.securityserver.restapi.service.CertificateAlreadyExistsException;
import org.niis.xroad.securityserver.restapi.service.CertificateNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.CsrNotFoundException;
import org.niis.xroad.securityserver.restapi.service.GlobalConfOutdatedException;
import org.niis.xroad.securityserver.restapi.service.InvalidCertificateException;
import org.niis.xroad.securityserver.restapi.service.KeyNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ManagementRequestSendingFailedException;
import org.niis.xroad.securityserver.restapi.service.PossibleActionEnum;
import org.niis.xroad.securityserver.restapi.service.TokenCertificateService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;

/**
 * certificates api
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class TokenCertificatesApiController implements TokenCertificatesApi {

    private final TokenCertificateService tokenCertificateService;
    private final TokenCertificateConverter tokenCertificateConverter;
    private final PossibleActionConverter possibleActionConverter;
    private final AuditDataHelper auditDataHelper;
    private final SecurityHelper securityHelper;

    @Override
    @PreAuthorize("hasAnyAuthority('ACTIVATE_DISABLE_AUTH_CERT','ACTIVATE_DISABLE_SIGN_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.ACTIVATE_CERT)
    public ResponseEntity<Void> activateCertificate(String hash) {
        try {
            tokenCertificateService.activateCertificate(hash);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ACTIVATE_DISABLE_AUTH_CERT','ACTIVATE_DISABLE_SIGN_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.DISABLE_CERT)
    public ResponseEntity<Void> disableCertificate(String hash) {
        try {
            tokenCertificateService.deactivateCertificate(hash);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('IMPORT_AUTH_CERT', 'IMPORT_SIGN_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.IMPORT_CERT_FILE)
    public ResponseEntity<TokenCertificate> importCertificate(Resource certificateResource) {
        // there's no filename since we only get a binary application/octet-stream.
        // Have audit log anyway (null behaves as no-op) in case different content type is added later
        String filename = certificateResource.getFilename();
        auditDataHelper.put(RestApiAuditProperty.CERT_FILE_NAME, filename);

        byte[] certificateBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(certificateResource);
        CertificateInfo certificate = null;
        try {
            certificate = tokenCertificateService.importCertificate(certificateBytes);
        } catch (ClientNotFoundException | KeyNotFoundException
                | TokenCertificateService.WrongCertificateUsageException
                | InvalidCertificateException
                | TokenCertificateService.AuthCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (GlobalConfOutdatedException | CertificateAlreadyExistsException | CsrNotFoundException e) {
            throw new ConflictException(e);
        }
        TokenCertificate tokenCertificate = tokenCertificateConverter.convert(certificate);
        return ControllerUtil.createCreatedResponse("/api/token-certificates/{hash}", tokenCertificate,
                tokenCertificate.getCertificateDetails().getHash());
    }

    @Override
    @PreAuthorize("hasAnyAuthority('VIEW_AUTH_CERT', 'VIEW_SIGN_CERT', 'VIEW_UNKNOWN_CERT')")
    public ResponseEntity<TokenCertificate> getCertificate(String hash) {
        CertificateInfo certificateInfo;
        try {
            certificateInfo = tokenCertificateService.getCertificateInfo(hash);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }

        // verify that correct permission exists, based on cert type
        X509Certificate x509Certificate = null;
        String requiredAuthority = null;
        try {
            x509Certificate = tokenCertificateService.convertToX509Certificate(certificateInfo.getCertificateBytes());
        } catch (InvalidCertificateException e) {
            throw new InternalServerErrorException(e);
        }
        if (tokenCertificateService.isValidAuthCert(x509Certificate)) {
            requiredAuthority = "VIEW_AUTH_CERT";
        } else if (tokenCertificateService.isValidSignCert(x509Certificate)) {
            requiredAuthority = "VIEW_SIGN_CERT";
        } else {
            requiredAuthority = "VIEW_UNKNOWN_CERT";
        }
        securityHelper.verifyAuthority(requiredAuthority);

        TokenCertificate tokenCertificate = tokenCertificateConverter.convert(certificateInfo);
        return new ResponseEntity<>(tokenCertificate, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('IMPORT_AUTH_CERT', 'IMPORT_SIGN_CERT', 'IMPORT_UNKNOWN_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.IMPORT_CERT_TOKEN)
    public ResponseEntity<TokenCertificate> importCertificateFromToken(String hash) {
        CertificateInfo certificate = null;
        try {
            certificate = tokenCertificateService.importCertificateFromToken(hash);
        } catch (ClientNotFoundException | KeyNotFoundException
                | TokenCertificateService.WrongCertificateUsageException
                | InvalidCertificateException
                | TokenCertificateService.AuthCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (GlobalConfOutdatedException | CertificateAlreadyExistsException | CsrNotFoundException
                | ActionNotPossibleException e) {
            throw new ConflictException(e);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        TokenCertificate tokenCertificate = tokenCertificateConverter.convert(certificate);
        return ControllerUtil.createCreatedResponse("/api/token-certificates/{hash}", tokenCertificate,
                tokenCertificate.getCertificateDetails().getHash());
    }

    @Override
    @PreAuthorize("hasAnyAuthority('DELETE_AUTH_CERT','DELETE_SIGN_CERT','DELETE_UNKNOWN_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_CERT)
    public ResponseEntity<Void> deleteCertificate(String hash) {
        try {
            tokenCertificateService.deleteCertificate(hash);
        } catch (CertificateNotFoundException | KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('VIEW_KEYS','VIEW_SIGN_CERT','VIEW_AUTH_CERT','VIEW_UNKNOWN_CERT')")
    public ResponseEntity<List<PossibleAction>> getPossibleActionsForCertificate(String hash) {
        try {
            EnumSet<PossibleActionEnum> actions = tokenCertificateService
                    .getPossibleActionsForCertificate(hash);
            return new ResponseEntity<>(possibleActionConverter.convert(actions), HttpStatus.OK);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_AUTH_CERT_REG_REQ')")
    @AuditEventMethod(event = RestApiAuditEvent.REGISTER_AUTH_CERT)
    public ResponseEntity<Void> registerCertificate(String hash, SecurityServerAddress securityServerAddress) {
        try {
            tokenCertificateService.registerAuthCert(hash, securityServerAddress.getAddress());
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (InvalidCertificateException
                | TokenCertificateService.SignCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException | KeyNotFoundException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_AUTH_CERT_DEL_REQ')")
    @AuditEventMethod(event = RestApiAuditEvent.UNREGISTER_AUTH_CERT)
    public ResponseEntity<Void> unregisterAuthCertificate(String hash) {
        try {
            tokenCertificateService.unregisterAuthCert(hash);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (InvalidCertificateException
                | TokenCertificateService.SignCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException | KeyNotFoundException e) {
            throw new ConflictException(e);
        } catch (ManagementRequestSendingFailedException e) {
            throw new InternalServerErrorException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_AUTH_CERT_DEL_REQ')")
    @AuditEventMethod(event = RestApiAuditEvent.SKIP_UNREGISTER_AUTH_CERT)
    public ResponseEntity<Void> markAuthCertForDeletion(String hash) {
        try {
            tokenCertificateService.markAuthCertForDeletion(hash);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (InvalidCertificateException
                | TokenCertificateService.SignCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException | KeyNotFoundException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

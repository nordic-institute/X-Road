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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.converter.PossibleActionConverter;
import org.niis.xroad.restapi.converter.TokenCertificateConverter;
import org.niis.xroad.restapi.openapi.model.PossibleAction;
import org.niis.xroad.restapi.openapi.model.SecurityServerAddress;
import org.niis.xroad.restapi.openapi.model.TokenCertificate;
import org.niis.xroad.restapi.service.ActionNotPossibleException;
import org.niis.xroad.restapi.service.CertificateAlreadyExistsException;
import org.niis.xroad.restapi.service.CertificateNotFoundException;
import org.niis.xroad.restapi.service.ClientNotFoundException;
import org.niis.xroad.restapi.service.CsrNotFoundException;
import org.niis.xroad.restapi.service.GlobalConfOutdatedException;
import org.niis.xroad.restapi.service.InvalidCertificateException;
import org.niis.xroad.restapi.service.KeyNotFoundException;
import org.niis.xroad.restapi.service.ManagementRequestSendingFailedException;
import org.niis.xroad.restapi.service.PossibleActionEnum;
import org.niis.xroad.restapi.service.TokenCertificateService;
import org.niis.xroad.restapi.util.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.EnumSet;
import java.util.List;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ACTIVATE_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DISABLE_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.IMPORT_CERT_FILE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.IMPORT_CERT_TOKEN;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REGISTER_AUTH_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.SKIP_UNREGISTER_AUTH_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.UNREGISTER_AUTH_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_FILE_NAME;

/**
 * certificates api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class TokenCertificatesApiController implements TokenCertificatesApi {

    private final TokenCertificateService tokenCertificateService;
    private final TokenCertificateConverter tokenCertificateConverter;
    private final PossibleActionConverter possibleActionConverter;
    private final AuditDataHelper auditDataHelper;

    @Autowired
    public TokenCertificatesApiController(TokenCertificateService tokenCertificateService,
            TokenCertificateConverter tokenCertificateConverter, PossibleActionConverter possibleActionConverter,
            AuditDataHelper auditDataHelper) {
        this.tokenCertificateService = tokenCertificateService;
        this.tokenCertificateConverter = tokenCertificateConverter;
        this.possibleActionConverter = possibleActionConverter;
        this.auditDataHelper = auditDataHelper;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ACTIVATE_DISABLE_AUTH_CERT','ACTIVATE_DISABLE_SIGN_CERT')")
    @AuditEventMethod(event = ACTIVATE_CERT)
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
    @AuditEventMethod(event = DISABLE_CERT)
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
    @AuditEventMethod(event = IMPORT_CERT_FILE)
    public ResponseEntity<TokenCertificate> importCertificate(Resource certificateResource) {
        // there's no filename since we only get a binary application/octet-stream.
        // Have audit log anyway (null behaves as no-op) in case different content type is added later
        String filename = certificateResource.getFilename();
        auditDataHelper.put(CERT_FILE_NAME, filename);

        byte[] certificateBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(certificateResource);
        CertificateInfo certificate = null;
        try {
            certificate = tokenCertificateService.importCertificate(certificateBytes);
        } catch (GlobalConfOutdatedException | ClientNotFoundException | KeyNotFoundException
                | TokenCertificateService.WrongCertificateUsageException
                | InvalidCertificateException
                | TokenCertificateService.AuthCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (CertificateAlreadyExistsException | CsrNotFoundException e) {
            throw new ConflictException(e);
        }
        TokenCertificate tokenCertificate = tokenCertificateConverter.convert(certificate);
        return ApiUtil.createCreatedResponse("/api/token-certificates/{hash}", tokenCertificate,
                tokenCertificate.getCertificateDetails().getHash());
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CERT')")
    public ResponseEntity<TokenCertificate> getCertificate(String hash) {
        CertificateInfo certificateInfo;
        try {
            certificateInfo = tokenCertificateService.getCertificateInfo(hash);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }

        TokenCertificate tokenCertificate = tokenCertificateConverter.convert(certificateInfo);
        return new ResponseEntity<>(tokenCertificate, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('IMPORT_AUTH_CERT', 'IMPORT_SIGN_CERT')")
    @AuditEventMethod(event = IMPORT_CERT_TOKEN)
    public ResponseEntity<TokenCertificate> importCertificateFromToken(String hash) {
        CertificateInfo certificate = null;
        try {
            certificate = tokenCertificateService.importCertificateFromToken(hash);
        } catch (GlobalConfOutdatedException | ClientNotFoundException | KeyNotFoundException
                | TokenCertificateService.WrongCertificateUsageException
                | InvalidCertificateException
                | TokenCertificateService.AuthCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (CertificateAlreadyExistsException | CsrNotFoundException
                | ActionNotPossibleException e) {
            throw new ConflictException(e);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        TokenCertificate tokenCertificate = tokenCertificateConverter.convert(certificate);
        return ApiUtil.createCreatedResponse("/api/token-certificates/{hash}", tokenCertificate,
                tokenCertificate.getCertificateDetails().getHash());
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_AUTH_CERT') or hasAuthority('DELETE_SIGN_CERT')")
    @AuditEventMethod(event = DELETE_CERT)
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
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
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
    @AuditEventMethod(event = REGISTER_AUTH_CERT)
    public ResponseEntity<Void> registerCertificate(String hash, SecurityServerAddress securityServerAddress) {
        try {
            tokenCertificateService.registerAuthCert(hash, securityServerAddress.getAddress());
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException
                | InvalidCertificateException
                | TokenCertificateService.SignCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (ActionNotPossibleException | KeyNotFoundException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_AUTH_CERT_DEL_REQ')")
    @AuditEventMethod(event = UNREGISTER_AUTH_CERT)
    public ResponseEntity<Void> unregisterAuthCertificate(String hash) {
        try {
            tokenCertificateService.unregisterAuthCert(hash);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | InvalidCertificateException
                | TokenCertificateService.SignCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (ActionNotPossibleException | KeyNotFoundException e) {
            throw new ConflictException(e);
        } catch (ManagementRequestSendingFailedException e) {
            throw new InternalServerErrorException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_AUTH_CERT_DEL_REQ')")
    @AuditEventMethod(event = SKIP_UNREGISTER_AUTH_CERT)
    public ResponseEntity<Void> markAuthCertForDeletion(String hash) {
        try {
            tokenCertificateService.markAuthCertForDeletion(hash);
        } catch (CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | InvalidCertificateException
                | TokenCertificateService.SignCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (ActionNotPossibleException | KeyNotFoundException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

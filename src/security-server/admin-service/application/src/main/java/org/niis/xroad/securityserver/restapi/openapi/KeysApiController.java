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

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.InternalServerErrorException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.converter.CsrFormatMapping;
import org.niis.xroad.securityserver.restapi.converter.KeyConverter;
import org.niis.xroad.securityserver.restapi.converter.KeyUsageTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.PossibleActionConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrFormatDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrGenerateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyNameDto;
import org.niis.xroad.securityserver.restapi.openapi.model.PossibleActionDto;
import org.niis.xroad.securityserver.restapi.service.ActionNotPossibleException;
import org.niis.xroad.securityserver.restapi.service.CertificateAlreadyExistsException;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.CsrNotFoundException;
import org.niis.xroad.securityserver.restapi.service.DnFieldHelper;
import org.niis.xroad.securityserver.restapi.service.GlobalConfOutdatedException;
import org.niis.xroad.securityserver.restapi.service.InvalidCertificateException;
import org.niis.xroad.securityserver.restapi.service.KeyNotFoundException;
import org.niis.xroad.securityserver.restapi.service.KeyService;
import org.niis.xroad.securityserver.restapi.service.PossibleActionEnum;
import org.niis.xroad.securityserver.restapi.service.ServerConfService;
import org.niis.xroad.securityserver.restapi.service.TokenCertificateService;
import org.niis.xroad.securityserver.restapi.service.WrongKeyUsageException;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.EnumSet;
import java.util.List;

/**
 * keys controller
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class KeysApiController implements KeysApi {

    private final KeyService keyService;
    private final KeyConverter keyConverter;
    private final TokenCertificateService tokenCertificateService;
    private final ServerConfService serverConfService;
    private final CsrFilenameCreator csrFilenameCreator;
    private final PossibleActionConverter possibleActionConverter;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    @Override
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public ResponseEntity<KeyDto> getKey(String keyId) {
        KeyDto keyDto = getKeyFromService(keyId);
        return new ResponseEntity<>(keyDto, HttpStatus.OK);
    }

    private KeyDto getKeyFromService(String keyId) {
        try {
            KeyInfo keyInfo = keyService.getKey(keyId);
            return keyConverter.convert(keyInfo);
        } catch (KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_KEY_FRIENDLY_NAME')")
    @AuditEventMethod(event = RestApiAuditEvent.UPDATE_KEY_NAME)
    public ResponseEntity<KeyDto> updateKey(String id, KeyNameDto keyNameDto) {
        KeyInfo keyInfo = null;
        try {
            keyInfo = keyService.updateKeyFriendlyName(id, keyNameDto.getName());
        } catch (KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        KeyDto keyDto = keyConverter.convert(keyInfo);
        return new ResponseEntity<>(keyDto, HttpStatus.OK);
    }

    @SuppressWarnings({"squid:S3655", "checkstyle:LineLength"}) // squid: see reason below. checkstyle: for readability
    @Override
    @PreAuthorize("(hasAuthority('GENERATE_AUTH_CERT_REQ') and "
            + "#csrGenerateDto.keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).AUTHENTICATION and"
            + "(!#csrGenerateDto.acmeOrder or hasAuthority('IMPORT_AUTH_CERT')))"
            + " or (hasAuthority('GENERATE_SIGN_CERT_REQ') and "
            + "#csrGenerateDto.keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).SIGNING and"
            + "(!#csrGenerateDto.acmeOrder or hasAuthority('IMPORT_SIGN_CERT')))")
    @AuditEventMethod(event = RestApiAuditEvent.GENERATE_CSR)
    public ResponseEntity<Resource> generateCsr(String keyId, CsrGenerateDto csrGenerateDto) {

        // since keyUsageInfo is mandatory parameter
        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(csrGenerateDto.getKeyUsageType()).orElseThrow();
        ClientId.Conf memberId = null;
        if (KeyUsageInfo.SIGNING == keyUsageInfo) {
            memberId = clientIdConverter.convertId(csrGenerateDto.getMemberId());
        }

        // since csr format is mandatory parameter
        CertificateRequestFormat csrFormat = CsrFormatMapping.map(csrGenerateDto.getCsrFormat()).orElseThrow();

        byte[] csr;
        try {
            csr = tokenCertificateService.generateCertRequest(keyId,
                    memberId,
                    keyUsageInfo,
                    csrGenerateDto.getCaName(),
                    csrGenerateDto.getSubjectFieldValues(),
                    csrFormat,
                    csrGenerateDto.getAcmeOrder()).certRequest();
        } catch (WrongKeyUsageException | DnFieldHelper.InvalidDnParameterException
                 | ClientNotFoundException | CertificateAuthorityNotFoundException
                 | TokenCertificateService.AuthCertificateNotSupportedException e) {
            throw new BadRequestException(e);
        } catch (KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException | GlobalConfOutdatedException | CertificateAlreadyExistsException
                 | CsrNotFoundException e) {
            throw new ConflictException(e);
        } catch (TokenCertificateService.WrongCertificateUsageException | InvalidCertificateException e) {
            throw new InternalServerErrorException(e);
        }
        if (BooleanUtils.isTrue(csrGenerateDto.getAcmeOrder())) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        String filename = csrFilenameCreator.createCsrFilename(keyUsageInfo, csrFormat, memberId,
                serverConfService.getSecurityServerId());

        return ControllerUtil.createAttachmentResourceResponse(csr, filename);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_AUTH_CERT') or hasAuthority('DELETE_SIGN_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_CSR)
    public ResponseEntity<Void> deleteCsr(String keyId, String csrId) {
        try {
            tokenCertificateService.deleteCsr(csrId);
        } catch (KeyNotFoundException | CsrNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public ResponseEntity<List<PossibleActionDto>> getPossibleActionsForCsr(String id, String csrId) {
        try {
            EnumSet<PossibleActionEnum> actions = tokenCertificateService
                    .getPossibleActionsForCsr(csrId);
            return new ResponseEntity<>(possibleActionConverter.convert(actions), HttpStatus.OK);
        } catch (CsrNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public ResponseEntity<List<PossibleActionDto>> getPossibleActionsForKey(String keyId) {
        try {
            EnumSet<PossibleActionEnum> actions = keyService.getPossibleActionsForKey(keyId);
            return new ResponseEntity<>(possibleActionConverter.convert(actions), HttpStatus.OK);
        } catch (KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
    }

    @Override
    @PreAuthorize("hasAnyAuthority('DELETE_KEY', 'DELETE_AUTH_KEY', 'DELETE_SIGN_KEY')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_KEY)
    public ResponseEntity<Void> deleteKey(String keyId, Boolean ignoreWarnings) {
        try {
            keyService.deleteKey(keyId, ignoreWarnings);
        } catch (KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException e) {
            throw new ConflictException(e);
        } catch (UnhandledWarningsException e) {
            throw new BadRequestException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('GENERATE_AUTH_CERT_REQ', 'GENERATE_SIGN_CERT_REQ')")
    public ResponseEntity<Resource> downloadCsr(String keyId, String csrId, CsrFormatDto csrFormatDto) {

        // since csr format is mandatory parameter
        CertificateRequestFormat certificateRequestFormat = CsrFormatMapping.map(csrFormatDto).orElseThrow();
        SignerRpcClient.GeneratedCertRequestInfo csrInfo;
        try {
            csrInfo = tokenCertificateService.regenerateCertRequest(keyId, csrId, certificateRequestFormat);
        } catch (KeyNotFoundException | CsrNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }

        String filename = csrFilenameCreator.createCsrFilename(csrInfo.keyUsage(),
                certificateRequestFormat, csrInfo.memberId(),
                serverConfService.getSecurityServerId());

        return ControllerUtil.createAttachmentResourceResponse(csrInfo.certRequest(), filename);
    }
}

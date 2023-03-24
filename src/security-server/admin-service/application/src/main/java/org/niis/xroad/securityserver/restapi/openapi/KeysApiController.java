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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.commonui.SignerProxy.GeneratedCertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.CertificateRequestFormat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.converter.CsrFormatMapping;
import org.niis.xroad.securityserver.restapi.converter.KeyConverter;
import org.niis.xroad.securityserver.restapi.converter.KeyUsageTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.PossibleActionConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrFormat;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrGenerate;
import org.niis.xroad.securityserver.restapi.openapi.model.Key;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyName;
import org.niis.xroad.securityserver.restapi.openapi.model.PossibleAction;
import org.niis.xroad.securityserver.restapi.service.ActionNotPossibleException;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.CsrNotFoundException;
import org.niis.xroad.securityserver.restapi.service.DnFieldHelper;
import org.niis.xroad.securityserver.restapi.service.GlobalConfOutdatedException;
import org.niis.xroad.securityserver.restapi.service.KeyNotFoundException;
import org.niis.xroad.securityserver.restapi.service.KeyService;
import org.niis.xroad.securityserver.restapi.service.PossibleActionEnum;
import org.niis.xroad.securityserver.restapi.service.ServerConfService;
import org.niis.xroad.securityserver.restapi.service.TokenCertificateService;
import org.niis.xroad.securityserver.restapi.service.WrongKeyUsageException;
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
    public ResponseEntity<Key> getKey(String keyId) {
        Key key = getKeyFromService(keyId);
        return new ResponseEntity<>(key, HttpStatus.OK);
    }

    private Key getKeyFromService(String keyId) {
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
    public ResponseEntity<Key> updateKey(String id, KeyName keyName) {
        KeyInfo keyInfo = null;
        try {
            keyInfo = keyService.updateKeyFriendlyName(id, keyName.getName());
        } catch (KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        Key key = keyConverter.convert(keyInfo);
        return new ResponseEntity<>(key, HttpStatus.OK);
    }

    @SuppressWarnings({"squid:S3655", "checkstyle:LineLength"}) // squid: see reason below. checkstyle: for readability
    @Override
    @PreAuthorize("(hasAuthority('GENERATE_AUTH_CERT_REQ') and "
            + "#csrGenerate.keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageType).AUTHENTICATION)"
            + " or (hasAuthority('GENERATE_SIGN_CERT_REQ') and "
            + "#csrGenerate.keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageType).SIGNING)")
    @AuditEventMethod(event = RestApiAuditEvent.GENERATE_CSR)
    public ResponseEntity<Resource> generateCsr(String keyId, CsrGenerate csrGenerate) {

        // squid:S3655 throwing NoSuchElementException if there is no value present is
        // fine since keyUsageInfo is mandatory parameter
        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(csrGenerate.getKeyUsageType()).get();
        ClientId.Conf memberId = null;
        if (KeyUsageInfo.SIGNING == keyUsageInfo) {
            // memberId not used for authentication csrs
            memberId = clientIdConverter.convertId(csrGenerate.getMemberId());
        }

        // squid:S3655 throwing NoSuchElementException if there is no value present is
        // fine since csr format is mandatory parameter
        CertificateRequestFormat csrFormat = CsrFormatMapping.map(csrGenerate.getCsrFormat()).get();

        byte[] csr;
        try {
            csr = tokenCertificateService.generateCertRequest(keyId,
                    memberId,
                    keyUsageInfo,
                    csrGenerate.getCaName(),
                    csrGenerate.getSubjectFieldValues(),
                    csrFormat).getCertRequest();
        } catch (WrongKeyUsageException | DnFieldHelper.InvalidDnParameterException
                | ClientNotFoundException | CertificateAuthorityNotFoundException e) {
            throw new BadRequestException(e);
        } catch (KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
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
    public ResponseEntity<List<PossibleAction>> getPossibleActionsForCsr(String id, String csrId) {
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
    public ResponseEntity<List<PossibleAction>> getPossibleActionsForKey(String keyId) {
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
    public ResponseEntity<Resource> downloadCsr(String keyId, String csrId, CsrFormat csrFormat) {

        // squid:S3655 throwing NoSuchElementException if there is no value present is
        // fine since csr format is mandatory parameter
        CertificateRequestFormat certificateRequestFormat = CsrFormatMapping.map(csrFormat).get();
        GeneratedCertRequestInfo csrInfo;
        try {
            csrInfo = tokenCertificateService.regenerateCertRequest(keyId, csrId, certificateRequestFormat);
        } catch (KeyNotFoundException | CsrNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }

        String filename = csrFilenameCreator.createCsrFilename(csrInfo.getKeyUsage(),
                certificateRequestFormat, csrInfo.getMemberId(),
                serverConfService.getSecurityServerId());

        return ControllerUtil.createAttachmentResourceResponse(csrInfo.getCertRequest(), filename);
    }

}

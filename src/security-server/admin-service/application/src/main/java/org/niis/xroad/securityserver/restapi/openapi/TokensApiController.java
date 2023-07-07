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
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
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
import org.niis.xroad.securityserver.restapi.converter.CsrFormatMapping;
import org.niis.xroad.securityserver.restapi.converter.KeyConverter;
import org.niis.xroad.securityserver.restapi.converter.KeyUsageTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.TokenConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrGenerate;
import org.niis.xroad.securityserver.restapi.openapi.model.Key;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabel;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelWithCsrGenerate;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyWithCertificateSigningRequestId;
import org.niis.xroad.securityserver.restapi.openapi.model.Token;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenName;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenPassword;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenPinUpdate;
import org.niis.xroad.securityserver.restapi.service.ActionNotPossibleException;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.DnFieldHelper;
import org.niis.xroad.securityserver.restapi.service.InvalidCharactersException;
import org.niis.xroad.securityserver.restapi.service.KeyAndCertificateRequestService;
import org.niis.xroad.securityserver.restapi.service.KeyService;
import org.niis.xroad.securityserver.restapi.service.TokenNotFoundException;
import org.niis.xroad.securityserver.restapi.service.TokenService;
import org.niis.xroad.securityserver.restapi.service.WeakPinException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Set;

/**
 * tokens controller
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class TokensApiController implements TokensApi {

    private final KeyConverter keyConverter;
    private final KeyService keyService;
    private final TokenService tokenService;
    private final TokenConverter tokenConverter;
    private final KeyAndCertificateRequestService keyAndCertificateRequestService;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    @Override
    public ResponseEntity<Set<Token>> getTokens() {
        List<TokenInfo> tokenInfos = tokenService.getAllTokens();
        Set<Token> tokens = tokenConverter.convert(tokenInfos);
        return new ResponseEntity<>(tokens, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public ResponseEntity<Token> getToken(String id) {
        Token token = getTokenFromService(id);
        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('ACTIVATE_DEACTIVATE_TOKEN')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.LOGIN_TOKEN)
    public ResponseEntity<Token> loginToken(String id, TokenPassword tokenPassword) {
        if (tokenPassword == null
                || tokenPassword.getPassword() == null
                || tokenPassword.getPassword().isEmpty()) {
            throw new BadRequestException("Missing token password");
        }
        char[] password = tokenPassword.getPassword().toCharArray();
        try {
            tokenService.activateToken(id, password);
        } catch (TokenNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (TokenService.PinIncorrectException e) {
            throw new BadRequestException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        Token token = getTokenFromService(id);
        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('ACTIVATE_DEACTIVATE_TOKEN')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.LOGOUT_TOKEN)
    public ResponseEntity<Token> logoutToken(String id) {
        try {
            tokenService.deactivateToken(id);
        } catch (TokenNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        Token token = getTokenFromService(id);
        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    private Token getTokenFromService(String id) {
        TokenInfo tokenInfo = null;
        try {
            tokenInfo = tokenService.getToken(id);
        } catch (TokenNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return tokenConverter.convert(tokenInfo);
    }

    @PreAuthorize("hasAuthority('EDIT_TOKEN_FRIENDLY_NAME')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.UPDATE_TOKEN_NAME)
    public ResponseEntity<Token> updateToken(String id, TokenName tokenName) {
        try {
            TokenInfo tokenInfo = tokenService.updateTokenFriendlyName(id, tokenName.getName());
            Token token = tokenConverter.convert(tokenInfo);
            return new ResponseEntity<>(token, HttpStatus.OK);
        } catch (TokenNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
    }

    @PreAuthorize("hasAuthority('GENERATE_KEY')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.GENERATE_KEY)
    public ResponseEntity<Key> addKey(String tokenId, KeyLabel keyLabel) {
        try {
            KeyInfo keyInfo = keyService.addKey(tokenId, keyLabel.getLabel());
            Key key = keyConverter.convert(keyInfo);
            return ControllerUtil.createCreatedResponse("/api/keys/{keyId}", key, key.getId());
        } catch (TokenNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('GENERATE_KEY') "
            + " and (hasAuthority('GENERATE_AUTH_CERT_REQ') or hasAuthority('GENERATE_SIGN_CERT_REQ'))")
    @AuditEventMethod(event = RestApiAuditEvent.GENERATE_KEY_AND_CSR)
    public ResponseEntity<KeyWithCertificateSigningRequestId> addKeyAndCsr(String tokenId,
            KeyLabelWithCsrGenerate keyLabelWithCsrGenerate) {

        // squid:S3655 throwing NoSuchElementException if there is no value present is
        // fine since keyUsageInfo is mandatory parameter
        CsrGenerate csrGenerate = keyLabelWithCsrGenerate.getCsrGenerateRequest();
        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(csrGenerate.getKeyUsageType()).get();
        ClientId.Conf memberId = null;
        if (KeyUsageInfo.SIGNING == keyUsageInfo) {
            // memberId not used for authentication csrs
            memberId = clientIdConverter.convertId(csrGenerate.getMemberId());
        }

        // squid:S3655 throwing NoSuchElementException if there is no value present is
        // fine since csr format is mandatory parameter
        CertificateRequestFormat csrFormat = CsrFormatMapping.map(csrGenerate.getCsrFormat()).get();

        KeyAndCertificateRequestService.KeyAndCertRequestInfo keyAndCertRequest;
        try {
            keyAndCertRequest = keyAndCertificateRequestService.addKeyAndCertRequest(
                    tokenId, keyLabelWithCsrGenerate.getKeyLabel(),
                    memberId,
                    keyUsageInfo,
                    csrGenerate.getCaName(),
                    csrGenerate.getSubjectFieldValues(),
                    csrFormat);
        } catch (ClientNotFoundException | CertificateAuthorityNotFoundException
                | DnFieldHelper.InvalidDnParameterException e) {
            throw new BadRequestException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        } catch (TokenNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }

        KeyWithCertificateSigningRequestId result = new KeyWithCertificateSigningRequestId();
        Key key = keyConverter.convert(keyAndCertRequest.getKeyInfo());
        result.setKey(key);
        result.setCsrId(keyAndCertRequest.getCertReqId());

        return new ResponseEntity<>(result, HttpStatus.OK);

    }

    @Override
    @PreAuthorize("hasAuthority('UPDATE_TOKEN_PIN')")
    @AuditEventMethod(event = RestApiAuditEvent.CHANGE_PIN_TOKEN)
    public ResponseEntity<Void> updateTokenPin(String id, TokenPinUpdate tokenPinUpdate) {
        try {
            tokenService.updateSoftwareTokenPin(id, tokenPinUpdate.getOldPin(), tokenPinUpdate.getNewPin());
        } catch (TokenNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (TokenService.PinIncorrectException | InvalidCharactersException | WeakPinException e) {
            throw new BadRequestException(e);
        } catch (ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}

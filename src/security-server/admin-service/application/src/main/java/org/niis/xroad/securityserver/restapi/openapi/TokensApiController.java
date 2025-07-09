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

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.securityserver.restapi.converter.CsrFormatMapping;
import org.niis.xroad.securityserver.restapi.converter.KeyConverter;
import org.niis.xroad.securityserver.restapi.converter.KeyUsageTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.TokenConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrGenerateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelWithCsrGenerateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyWithCertificateSigningRequestIdDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenNameDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenPasswordDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenPinUpdateDto;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.CsrNotFoundException;
import org.niis.xroad.securityserver.restapi.service.InvalidCertificateException;
import org.niis.xroad.securityserver.restapi.service.KeyAndCertificateRequestService;
import org.niis.xroad.securityserver.restapi.service.KeyService;
import org.niis.xroad.securityserver.restapi.service.TokenCertificateService;
import org.niis.xroad.securityserver.restapi.service.TokenService;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Set;

import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.MISSING_TOKEN_PASSWORD;

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
    public ResponseEntity<Set<TokenDto>> getTokens() {
        List<TokenInfo> tokenInfos = tokenService.getAllTokens();
        Set<TokenDto> tokens = tokenConverter.convert(tokenInfos);
        return new ResponseEntity<>(tokens, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public ResponseEntity<TokenDto> getToken(String id) {
        TokenDto token = getTokenFromService(id);
        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('ACTIVATE_DEACTIVATE_TOKEN')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.LOGIN_TOKEN)
    public ResponseEntity<TokenDto> loginToken(String id, TokenPasswordDto tokenPasswordDto) {
        if (tokenPasswordDto == null
                || tokenPasswordDto.getPassword() == null
                || tokenPasswordDto.getPassword().isEmpty()) {
            throw new BadRequestException(MISSING_TOKEN_PASSWORD.build());
        }
        char[] password = tokenPasswordDto.getPassword().toCharArray();

        tokenService.activateToken(id, password);

        TokenDto token = getTokenFromService(id);
        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('ACTIVATE_DEACTIVATE_TOKEN')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.LOGOUT_TOKEN)
    public ResponseEntity<TokenDto> logoutToken(String id) {

        tokenService.deactivateToken(id);

        TokenDto token = getTokenFromService(id);
        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    private TokenDto getTokenFromService(String id) {
        TokenInfo tokenInfo = tokenService.getToken(id);
        return tokenConverter.convert(tokenInfo);
    }

    @PreAuthorize("hasAuthority('EDIT_TOKEN_FRIENDLY_NAME')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.UPDATE_TOKEN_NAME)
    public ResponseEntity<TokenDto> updateToken(String id, TokenNameDto tokenNameDto) {
        TokenInfo tokenInfo = tokenService.updateTokenFriendlyName(id, tokenNameDto.getName());
        TokenDto token = tokenConverter.convert(tokenInfo);
        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('DELETE_TOKEN')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_TOKEN)
    @Override
    public ResponseEntity<Void> deleteToken(String id) {
        tokenService.deleteToken(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PreAuthorize("hasAuthority('GENERATE_KEY')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.GENERATE_KEY)
    public ResponseEntity<KeyDto> addKey(String tokenId, KeyLabelDto keyLabelDto) {

        KeyInfo keyInfo = keyService.addKey(tokenId, keyLabelDto.getLabel(), KeyAlgorithm.RSA);
        KeyDto key = keyConverter.convert(keyInfo);
        return ControllerUtil.createCreatedResponse("/api/keys/{keyId}", key, key.getId());
    }

    @Override
    @PreAuthorize("hasAuthority('GENERATE_KEY') "
            + " and (hasAuthority('GENERATE_AUTH_CERT_REQ') or hasAuthority('GENERATE_SIGN_CERT_REQ'))"
            + " and (!#keyLabelWithCsrGenerateDto.csrGenerateRequest.acmeOrder"
            + "      or hasAuthority('IMPORT_AUTH_CERT') or hasAuthority('IMPORT_SIGN_CERT'))")
    @AuditEventMethod(event = RestApiAuditEvent.GENERATE_KEY_AND_CSR)
    public ResponseEntity<KeyWithCertificateSigningRequestIdDto> addKeyAndCsr(String tokenId,
                                                                              KeyLabelWithCsrGenerateDto keyLabelWithCsrGenerateDto) {

        // squid:S3655 throwing NoSuchElementException if there is no value present is
        // fine since keyUsageInfo is mandatory parameter
        CsrGenerateDto csrGenerate = keyLabelWithCsrGenerateDto.getCsrGenerateRequest();
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
                    tokenId, keyLabelWithCsrGenerateDto.getKeyLabel(),
                    memberId,
                    keyUsageInfo,
                    csrGenerate.getCaName(),
                    csrGenerate.getSubjectFieldValues(),
                    csrFormat,
                    csrGenerate.getAcmeOrder());
        } catch (ClientNotFoundException | CertificateAuthorityNotFoundException e) {
            throw new BadRequestException(e);
        } catch (CsrNotFoundException e) {
            throw new ConflictException(e);
        } catch (TokenCertificateService.WrongCertificateUsageException | InvalidCertificateException e) {
            throw new InternalServerErrorException(e);
        }

        KeyWithCertificateSigningRequestIdDto result = new KeyWithCertificateSigningRequestIdDto();
        KeyDto key = keyConverter.convert(keyAndCertRequest.getKeyInfo());
        result.setKey(key);
        result.setCsrId(keyAndCertRequest.getCertReqId());

        return new ResponseEntity<>(result, HttpStatus.OK);

    }

    @Override
    @PreAuthorize("hasAuthority('UPDATE_TOKEN_PIN')")
    @AuditEventMethod(event = RestApiAuditEvent.CHANGE_PIN_TOKEN)
    public ResponseEntity<Void> updateTokenPin(String id, TokenPinUpdateDto tokenPinUpdateDto) {

        tokenService.updateSoftwareTokenPin(id, tokenPinUpdateDto.getOldPin(), tokenPinUpdateDto.getNewPin());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}

/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.Error; // TO DO: rename Error, it's bad since java.lang
import org.niis.xroad.restapi.exceptions.InternalServerErrorException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.repository.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_PIN_INCORRECT;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_FOUND;
import static java.util.stream.Collectors.toList;

/**
 * token related service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class TokenService {

    @Autowired
    @Setter
    private TokenRepository tokenRepository;

    /**
     * get all tokens
     *
     * @return
     * @throws Exception
     */
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public List<TokenInfo> getAllTokens() throws Exception {
        return tokenRepository.getTokens();
    }

    /**
     * get all certificates for a given client.
     *
     * @param clientType client who's member certificates need to be
     *                   linked to
     * @return
     * @throws Exception
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public List<CertificateInfo> getAllTokens(ClientType clientType) throws Exception {
        List<TokenInfo> tokenInfos = getAllTokens();
        return tokenInfos.stream()
                .flatMap(tokenInfo -> tokenInfo.getKeyInfo().stream())
                .flatMap(keyInfo -> keyInfo.getCerts().stream())
                .filter(certificateInfo -> clientType.getIdentifier().memberEquals(certificateInfo.getMemberId()))
                .collect(toList());
    }

    /**
     * Activate a token
     * @param id id of token
     * @param password password for token
     * @throws Exception
     */
    @PreAuthorize("hasAuthority('ACTIVATE_TOKEN')")
    public void activateToken(String id, char[] password) throws IncorrectPinException {
        try {
            SignerProxy.activateToken(id, password);
        } catch (CodedException e) {
            log.info("codedexception=" + e);
            log.info("getFaultCode=" + e.getFaultCode());
            if (PIN_INCORRECT_FAULT_CODE.equals(e.getFaultCode())) {
                throw new IncorrectPinException(e);
            } else if (TOKEN_NOT_FOUND_FAULT_CODE.equals(e.getFaultCode())) {
                throw new TokenNotFoundException(e);
            } else {
                throw new UnspecifiedCoreCodedException(e);
            }
        } catch (Exception other) {
            throw new RuntimeException("token activation failed", other);
        }
    }


    private static final String PIN_INCORRECT_FAULT_CODE = SIGNER_X + "." + X_PIN_INCORRECT;
    private static final String TOKEN_NOT_FOUND_FAULT_CODE = SIGNER_X + "." + X_TOKEN_NOT_FOUND;

    public static final String ERROR_PIN_INCORRECT = "tokens.pin_incorrect";



    /**
     * This might be better as a checked exception. To document the service api properly.
     * Now exceptions are not documented in the method signature and it is a guessing game /
     * blindly trusting service layer to do what is correct. Maybe refactor later.
     */
    public static class IncorrectPinException extends BadRequestException {
        public IncorrectPinException(Throwable t) {
            super(t, new Error(ERROR_PIN_INCORRECT));
        }
    }
    public static class TokenNotFoundException extends NotFoundException {
        public TokenNotFoundException(Throwable t) {
            super(t);
        }
    }

    /**
     * uses error code "core." + <fault code from CodedException>
     */
    public static class UnspecifiedCoreCodedException extends InternalServerErrorException {
        public UnspecifiedCoreCodedException(CodedException c) {
            super(c, new Error("core." + c.getFaultCode()));
        }
    }

    /**
     * Deactivate a toke
     * @param id id of token
     * @throws Exception
     */
    @PreAuthorize("hasAuthority('DEACTIVATE_TOKEN')")
    public void deactiveToken(String id) {
        try {
            SignerProxy.deactivateToken(id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * TO DO: look into exception handling, can we do better...?
     * @param id
     * @return
     * @throws Exception
     */
    @PreAuthorize("hasAnyAuthority('ACTIVATE_TOKEN','DEACTIVATE_TOKEN')")
    public TokenInfo getToken(String id) throws Exception {
        return SignerProxy.getToken(id);
    }
}

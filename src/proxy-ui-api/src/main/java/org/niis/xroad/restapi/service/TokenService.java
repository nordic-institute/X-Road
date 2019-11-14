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
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Predicate;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_LOGIN_FAILED;
import static ee.ria.xroad.common.ErrorCodes.X_PIN_INCORRECT;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_FOUND;
import static java.util.stream.Collectors.toList;

/**
 * Service that handles tokens
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class TokenService {

    private final SignerProxyFacade signerProxyFacade;

    /**
     * TokenService constructor
     * @param signerProxyFacade
     */
    @Autowired
    public TokenService(SignerProxyFacade signerProxyFacade) {
        this.signerProxyFacade = signerProxyFacade;
    }

    /**
     * get all tokens
     *
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public List<TokenInfo> getAllTokens() {
        try {
            return signerProxyFacade.getTokens();
        } catch (Exception e) {
            throw new RuntimeException("could not list all tokens", e);
        }
    }

    /**
     * get all sign certificates for a given client.
     *
     * @param clientType client who's member certificates need to be
     *                   linked to
     * @return
     * @throws Exception
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public List<CertificateInfo> getSignCertificates(ClientType clientType) {
        return getCertificates(clientType, true);
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
    public List<CertificateInfo> getAllCertificates(ClientType clientType) {
        return getCertificates(clientType, false);
    }

    /**
     * Get all certificates for a given client
     * @param clientType
     * @param onlySignCertificates if true, return only signing certificates
     * @return
     */
    private List<CertificateInfo> getCertificates(ClientType clientType, boolean onlySignCertificates) {
        List<TokenInfo> tokenInfos = getAllTokens();
        Predicate<KeyInfo> keyInfoPredicate = keyInfo -> true;
        if (onlySignCertificates) {
            keyInfoPredicate = keyInfo -> keyInfo.isForSigning();
        }
        return tokenInfos.stream()
                .flatMap(tokenInfo -> tokenInfo.getKeyInfo().stream())
                .filter(keyInfoPredicate)
                .flatMap(keyInfo -> keyInfo.getCerts().stream())
                .filter(certificateInfo -> clientType.getIdentifier().memberEquals(certificateInfo.getMemberId()))
                .collect(toList());
    }


    /**
     * Activate a token
     * @param id id of token
     * @param password password for token
     * @throws TokenNotFoundException if token was not found
     * @throws PinIncorrectException if token login failed due to wrong ping
     */
    @PreAuthorize("hasAuthority('ACTIVATE_TOKEN')")
    public void activateToken(String id, char[] password) throws
            TokenNotFoundException, PinIncorrectException {
        try {
            signerProxyFacade.activateToken(id, password);
        } catch (CodedException e) {
            if (isCausedByTokenNotFound(e)) {
                throw new TokenNotFoundException(e);
            } else if (isCausedByIncorrectPin(e)) {
                throw new PinIncorrectException(e);
            } else {
                throw e;
            }
        } catch (Exception other) {
            throw new RuntimeException("token activation failed", other);
        }
    }

    /**
     * Deactivate a token
     * @param id id of token
     * @throws TokenNotFoundException if token was not found
     */
    @PreAuthorize("hasAuthority('DEACTIVATE_TOKEN')")
    public void deactivateToken(String id) throws TokenNotFoundException {
        try {
            signerProxyFacade.deactivateToken(id);
        } catch (CodedException e) {
            if (isCausedByTokenNotFound(e)) {
                throw new TokenNotFoundException(e);
            } else {
                throw e;
            }
        } catch (Exception other) {
            throw new RuntimeException("token deactivation failed", other);
        }
    }

    /**
     * return one token
     * @param id
     * @throws TokenNotFoundException if token was not found
     */
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public TokenInfo getToken(String id) throws TokenNotFoundException {
        try {
            return signerProxyFacade.getToken(id);
        } catch (CodedException e) {
            if (isCausedByTokenNotFound(e)) {
                throw new TokenNotFoundException(e);
            } else {
                throw e;
            }
        } catch (Exception other) {
            throw new RuntimeException("get token failed", other);
        }
    }

    private boolean isCausedByIncorrectPin(CodedException e) {
        if (PIN_INCORRECT_FAULT_CODE.equals(e.getFaultCode())) {
            return true;
        } else if (LOGIN_FAILED_FAULT_CODE.equals(e.getFaultCode())) {
            if (CKR_PIN_INCORRECT_MESSAGE.equals(e.getFaultString())) {
                // only way to detect HSM pin incorrect is by matching to codedException
                // fault string.
                return true;
            }
        }
        return false;
    }

    private boolean isCausedByTokenNotFound(CodedException e) {
        return TOKEN_NOT_FOUND_FAULT_CODE.equals(e.getFaultCode());
    }

    // detect a couple of CodedException error codes from core
    static final String PIN_INCORRECT_FAULT_CODE = SIGNER_X + "." + X_PIN_INCORRECT;
    static final String TOKEN_NOT_FOUND_FAULT_CODE = SIGNER_X + "." + X_TOKEN_NOT_FOUND;
    static final String LOGIN_FAILED_FAULT_CODE = SIGNER_X + "." + X_LOGIN_FAILED;
    static final String CKR_PIN_INCORRECT_MESSAGE = "Login failed: CKR_PIN_INCORRECT";

    /**
     * If token was not found
     */
    public static class TokenNotFoundException extends NotFoundException {

        public static final String ERROR_TOKEN_NOT_FOUND = "token_not_found";

        public TokenNotFoundException(String s) {
            super(s, createError());
        }

        public TokenNotFoundException(Throwable t) {
            super(t, createError());
        }

        private static ErrorDeviation createError() {
            return new ErrorDeviation(ERROR_TOKEN_NOT_FOUND);
        }
    }

    public static class PinIncorrectException extends ServiceException {

        public static final String ERROR_PIN_INCORRECT = "pin_incorrect";

        public PinIncorrectException(Throwable t) {
            super(t, createError());
        }

        private static ErrorDeviation createError() {
            return new ErrorDeviation(ERROR_PIN_INCORRECT);
        }

    }
}

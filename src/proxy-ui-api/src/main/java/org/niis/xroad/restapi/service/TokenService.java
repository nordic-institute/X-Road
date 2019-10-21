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
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.Error;
import org.niis.xroad.restapi.exceptions.InternalServerErrorException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
     * @throws Exception
     */
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public List<TokenInfo> getAllTokens() throws Exception {
        return signerProxyFacade.getTokens();
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
    public List<CertificateInfo> getAllCertificates(ClientType clientType) throws Exception {
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
     * @throws TokenNotFoundException if token was not found
     * @throws PinIncorrectException if token login failed due to wrong ping
     * @throws UnspecifiedCoreCodedException if login failed due to some other core
     * CodedException (for example pin being locked). Error code is "core." + original error,
     * for example core.Signer.LoginFailed
     */
    @PreAuthorize("hasAuthority('ACTIVATE_TOKEN')")
    public void activateToken(String id, char[] password) throws TokenNotFoundException,
            PinIncorrectException, UnspecifiedCoreCodedException {
        try {
            signerProxyFacade.activateToken(id, password);
        } catch (CodedException e) {
            throw mapToDeviationAwareRuntimeException(e);
        } catch (Exception other) {
            throw new RuntimeException("token activation failed", other);
        }
    }

    /**
     * Deactivate a token
     * @param id id of token
     * @throws TokenNotFoundException if token was not found
     * @throws UnspecifiedCoreCodedException if login failed due to some other core
     * CodedException (for example pin being locked). Error code is "core." + original error,
     * for example core.Signer.LoginFailed
     */
    @PreAuthorize("hasAuthority('DEACTIVATE_TOKEN')")
    public void deactivateToken(String id) throws TokenNotFoundException, UnspecifiedCoreCodedException {
        try {
            signerProxyFacade.deactivateToken(id);
        } catch (CodedException e) {
            throw mapToDeviationAwareRuntimeException(e);
        } catch (Exception other) {
            throw new RuntimeException("token deactivation failed", other);
        }
    }

    /**
     * return one token
     * @param id
     * @throws TokenNotFoundException if token was not found
     * @throws UnspecifiedCoreCodedException if login failed due to some other core
     * CodedException (for example pin being locked). Error code is "core." + original error,
     * for example core.Signer.LoginFailed
     */
    @PreAuthorize("hasAnyAuthority('ACTIVATE_TOKEN','DEACTIVATE_TOKEN')")
    public TokenInfo getToken(String id) throws TokenNotFoundException, UnspecifiedCoreCodedException {
        try {
            return signerProxyFacade.getToken(id);
        } catch (CodedException e) {
            throw mapToDeviationAwareRuntimeException(e);
        } catch (Exception other) {
            throw new RuntimeException("get token failed", other);
        }
    }

    /**
     * maps CodedException from into a correct DeviationAwareRuntimeException.
     * @return DeviationAwareRuntimeException:
     * - TokenNotFoundException if token was not found
     * - PinIncorrectException if token login failed due to wrong ping
     * - UnspecifiedCoreCodedException if login failed due to some other core
     * CodedException (for example pin being locked). Error code is "core." + original error,
     * for example core.Signer.LoginFailed
     */
    private DeviationAwareRuntimeException mapToDeviationAwareRuntimeException(CodedException e)
            throws TokenNotFoundException, PinIncorrectException,
            UnspecifiedCoreCodedException {
        log.debug("codedException faultCode=" + e.getFaultCode());

        // by default UnspecifiedCoreCodedException. Override, if we can detect something more
        // specific
        DeviationAwareRuntimeException exception = new UnspecifiedCoreCodedException(e);
        if (PIN_INCORRECT_FAULT_CODE.equals(e.getFaultCode())) {
            exception = new PinIncorrectException(e);
        } else if (LOGIN_FAILED_FAULT_CODE.equals(e.getFaultCode())) {
            if (CKR_PIN_INCORRECT_MESSAGE.equals(e.getFaultString())) {
                // only way to detect HSM pin incorrect is by matching to codedException
                // fault string.
                exception = new PinIncorrectException(e);
            }
        } else if (TOKEN_NOT_FOUND_FAULT_CODE.equals(e.getFaultCode())) {
            exception = new TokenNotFoundException(e);
        }
        return exception;
    }

    /**
     * Return one key
     * @param tokenId
     * @param keyId
     * @throws KeyNotFoundException if key was not found
     * @throws TokenNotFoundException if token was not found
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public KeyInfo getKey(String tokenId, String keyId) {
        TokenInfo tokenInfo = getToken(tokenId);
        Optional<KeyInfo> keyInfo = tokenInfo.getKeyInfo().stream()
                                            .filter(key -> keyId.equals(key.getId()))
                                            .findFirst();
        if (!keyInfo.isPresent()) {
            throw new KeyNotFoundException("key with id " + keyId + " not found");
        }

        return keyInfo.get();
    }

    // detect a couple of CodedException error codes from core
    static final String PIN_INCORRECT_FAULT_CODE = SIGNER_X + "." + X_PIN_INCORRECT;
    static final String TOKEN_NOT_FOUND_FAULT_CODE = SIGNER_X + "." + X_TOKEN_NOT_FOUND;
    static final String LOGIN_FAILED_FAULT_CODE = SIGNER_X + "." + X_LOGIN_FAILED;
    static final String CKR_PIN_INCORRECT_MESSAGE = "Login failed: CKR_PIN_INCORRECT";

    public static final String ERROR_PIN_INCORRECT = "tokens.login_failed_pin_incorrect";

    /**
     * These might be better as checked exceptions. To document the service api properly.
     * Now exceptions are not documented in the method signature and it is a guessing game /
     * blindly trusting service layer to do what is correct. Maybe refactor later.
     */
    public static class PinIncorrectException extends BadRequestException {
        public PinIncorrectException(Throwable t) {
            super(t, new Error(ERROR_PIN_INCORRECT));
        }
    }
    public static class TokenNotFoundException extends NotFoundException {
        public TokenNotFoundException(Throwable t) {
            super(t);
        }
    }
    public static class KeyNotFoundException extends NotFoundException {
        public KeyNotFoundException(String s) {
            super(s);
        }
    }

    /**
     * uses error code "core." + <fault code from CodedException>.
     * Error metadata = codedException.faultString
     */
    public static class UnspecifiedCoreCodedException extends InternalServerErrorException {
        public UnspecifiedCoreCodedException(CodedException c) {
            super(c, new Error("core." + c.getFaultCode(), c.getFaultString()));
            log.info("codedexception: " + c);
        }
    }


}

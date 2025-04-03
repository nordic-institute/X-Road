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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.securityserver.restapi.dto.TokenInitStatusInfo;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.ACTION_NOT_POSSIBLE;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INTERNAL_ERROR;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.TOKEN_FETCH_FAILED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.TOKEN_NOT_FOUND;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.TOKEN_PIN_INCORRECT;

/**
 * Service that handles tokens
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class TokenService {

    private final SignerRpcClient signerRpcClient;
    private final PossibleActionsRuleEngine possibleActionsRuleEngine;
    private final AuditDataHelper auditDataHelper;
    private final TokenPinValidator tokenPinValidator;

    /**
     * get all tokens
     *
     * @return
     */
    public List<TokenInfo> getAllTokens() {
        try {
            return signerRpcClient.getTokens();
        } catch (Exception e) {
            throw new ServiceException(TOKEN_FETCH_FAILED, e);
        }
    }

    /**
     * get all sign certificates for a given client.
     *
     * @param client client whose member certificates need to be
     *                   linked to
     * @return
     */
    public List<CertificateInfo> getSignCertificates(Client client) {
        return getCertificates(client, true);
    }

    /**
     * get all certificates for a given client.
     *
     * @param client client whose member certificates need to be
     *                   linked to
     * @return
     */
    public List<CertificateInfo> getAllCertificates(Client client) {
        return getCertificates(client, false);
    }

    /**
     * Get all certificates for a given client
     *
     * @param client
     * @param onlySignCertificates if true, return only signing certificates
     * @return
     */
    private List<CertificateInfo> getCertificates(Client client, boolean onlySignCertificates) {
        List<TokenInfo> tokenInfos = getAllTokens();
        Predicate<KeyInfo> keyInfoPredicate = keyInfo -> true;
        if (onlySignCertificates) {
            keyInfoPredicate = keyInfo -> keyInfo.isForSigning();
        }
        return tokenInfos.stream()
                .flatMap(tokenInfo -> tokenInfo.getKeyInfo().stream())
                .filter(keyInfoPredicate)
                .flatMap(keyInfo -> keyInfo.getCerts().stream())
                .filter(certificateInfo -> client.getIdentifier().memberEquals(certificateInfo.getMemberId()))
                .collect(toList());
    }

    /**
     * Activate a token
     *
     * @param id       id of token
     * @param password password for token
     * @throws TokenNotFoundException     if token was not found
     * @throws PinIncorrectException      if token login failed due to wrong ping
     * @throws ActionNotPossibleException if token activation was not possible
     */
    public void activateToken(String id, char[] password) throws
            TokenNotFoundException, PinIncorrectException, ActionNotPossibleException {

        // check that action is possible
        TokenInfo tokenInfo = getToken(id);

        auditDataHelper.put(tokenInfo);

        possibleActionsRuleEngine.requirePossibleTokenAction(PossibleActionEnum.TOKEN_ACTIVATE,
                tokenInfo);
        try {
            signerRpcClient.activateToken(id, password);
        } catch (SignerException e) {
            if (e.isCausedByTokenNotFound()) {
                throw new TokenNotFoundException(e);
            } else if (e.isCausedByIncorrectPin()) {
                throw new PinIncorrectException(e);
            } else {
                throw e;
            }
        } catch (CodedException ce) {
            throw ce;
        } catch (Exception other) {
            throw new ServiceException(INTERNAL_ERROR, other);
        }
    }

    /**
     * Deactivate a token
     *
     * @param id id of token
     * @throws TokenNotFoundException     if token was not found
     * @throws ActionNotPossibleException if deactivation was not possible
     */
    public void deactivateToken(String id) throws TokenNotFoundException, ActionNotPossibleException {

        // check that action is possible
        TokenInfo tokenInfo = getToken(id);

        auditDataHelper.put(tokenInfo);

        possibleActionsRuleEngine.requirePossibleTokenAction(PossibleActionEnum.TOKEN_DEACTIVATE,
                tokenInfo);

        try {
            signerRpcClient.deactivateToken(id);
        } catch (SignerException e) {
            if (e.isCausedByTokenNotFound()) {
                throw new TokenNotFoundException(e);
            } else {
                throw e;
            }
        } catch (CodedException ce) {
            throw ce;
        } catch (Exception other) {
            throw new ServiceException(INTERNAL_ERROR, other);
        }
    }

    /**
     * return one token
     *
     * @param id
     * @throws TokenNotFoundException if token was not found
     */
    public TokenInfo getToken(String id) throws TokenNotFoundException {
        try {
            return signerRpcClient.getToken(id);
        } catch (SignerException e) {
            if (e.isCausedByTokenNotFound()) {
                throw new TokenNotFoundException(e);
            } else {
                throw e;
            }
        } catch (CodedException ce) {
            throw ce;
        } catch (Exception other) {
            throw new ServiceException(INTERNAL_ERROR, other);
        }
    }

    /**
     * update token friendly name
     *
     * @param tokenId
     * @param friendlyName
     * @throws TokenNotFoundException if token was not found
     */
    public TokenInfo updateTokenFriendlyName(String tokenId, String friendlyName) throws TokenNotFoundException,
            ActionNotPossibleException {

        // check that updating friendly name is possible
        TokenInfo tokenInfo = getToken(tokenId);
        auditDataHelper.put(tokenInfo);
        auditDataHelper.put(RestApiAuditProperty.TOKEN_FRIENDLY_NAME, friendlyName); // Override old value with the new
        possibleActionsRuleEngine.requirePossibleTokenAction(PossibleActionEnum.EDIT_FRIENDLY_NAME,
                tokenInfo);

        try {
            signerRpcClient.setTokenFriendlyName(tokenId, friendlyName);
            tokenInfo = signerRpcClient.getToken(tokenId);
        } catch (SignerException e) {
            if (e.isCausedByTokenNotFound()) {
                throw new TokenNotFoundException(e);
            } else {
                throw e;
            }
        } catch (CodedException ce) {
            throw ce;
        } catch (Exception other) {
            throw new ServiceException(INTERNAL_ERROR, other);
        }
        return tokenInfo;
    }

    /**
     * Get TokenInfo for key id
     */
    public TokenInfo getTokenForKeyId(String keyId) throws KeyNotFoundException {
        try {
            return signerRpcClient.getTokenForKeyId(keyId);
        } catch (SignerException e) {
            if (e.isCausedByKeyNotFound()) {
                throw new KeyNotFoundException(e);
            } else {
                throw e;
            }
        } catch (CodedException ce) {
            throw ce;
        } catch (Exception other) {
            throw new ServiceException(INTERNAL_ERROR, other);
        }
    }

    /**
     * Get TokenInfoAndKeyId for certificate hash
     */
    public TokenInfoAndKeyId getTokenAndKeyIdForCertificateHash(String hash) throws KeyNotFoundException,
            CertificateNotFoundException {
        try {
            return signerRpcClient.getTokenAndKeyIdForCertHash(hash);
        } catch (SignerException e) {
            if (e.isCausedByKeyNotFound()) {
                throw new KeyNotFoundException(e);
            } else if (e.isCausedByCertNotFound()) {
                throw new CertificateNotFoundException(e);
            } else {
                throw e;
            }
        } catch (CodedException ce) {
            throw ce;
        } catch (Exception other) {
            throw new ServiceException(INTERNAL_ERROR, other);
        }
    }

    /**
     * Whether or not a software token exists AND it's status != TokenStatusInfo.NOT_INITIALIZED
     *
     * @return true/false
     */
    public boolean isSoftwareTokenInitialized() {
        boolean isSoftwareTokenInitialized = false;
        List<TokenInfo> tokens = getAllTokens();
        Optional<TokenInfo> firstSoftwareToken = tokens.stream()
                .filter(tokenInfo -> tokenInfo.getId().equals(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID))
                .findFirst();

        if (firstSoftwareToken.isPresent()) {
            TokenInfo token = firstSoftwareToken.get();
            isSoftwareTokenInitialized = token.getStatus() != TokenStatusInfo.NOT_INITIALIZED;
        }
        return isSoftwareTokenInitialized;
    }

    /**
     * Whether or not a software token exists AND it's status != TokenStatusInfo.NOT_INITIALIZED
     *
     * @return {@link TokenInitStatusInfo}
     */
    public TokenInitStatusInfo getSoftwareTokenInitStatus() {
        try {
            boolean isSoftwareTokenInitialized = isSoftwareTokenInitialized();
            if (isSoftwareTokenInitialized) {
                return TokenInitStatusInfo.INITIALIZED;
            } else {
                return TokenInitStatusInfo.NOT_INITIALIZED;
            }
        } catch (SignerException | ServiceException e) {
            log.error("Could not get software token status from signer", e);
            return TokenInitStatusInfo.UNKNOWN;
        }
    }

    /**
     * Get TokenInfoAndKeyId for csr id
     */
    public TokenInfoAndKeyId getTokenAndKeyIdForCertificateRequestId(String csrId) throws KeyNotFoundException,
            CsrNotFoundException {
        try {
            return signerRpcClient.getTokenAndKeyIdForCertRequestId(csrId);
        } catch (SignerException e) {
            if (e.isCausedByKeyNotFound()) {
                throw new KeyNotFoundException(e);
            } else if (e.isCausedByCsrNotFound()) {
                throw new CsrNotFoundException(e);
            } else {
                throw e;
            }
        } catch (CodedException ce) {
            throw ce;
        } catch (Exception other) {
            throw new ServiceException(INTERNAL_ERROR, other);
        }
    }

    /**
     * Check if there are any tokens that are not software tokens
     *
     * @return true if there are any other than software tokens present
     */
    public boolean hasHardwareTokens() {
        List<TokenInfo> allTokens = getAllTokens();
        return allTokens.stream().anyMatch(tokenInfo -> !PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID.equals(
                tokenInfo.getId()));
    }

    /**
     * Update the pin code for a token and it's keys
     *
     * @param tokenId ID of the token
     * @param oldPin  the old (current) passing pin
     * @param newPin  the new pin
     * @throws TokenNotFoundException token not found
     * @throws PinIncorrectException  incorrect pin
     */
    public void updateSoftwareTokenPin(String tokenId, String oldPin, String newPin)
            throws TokenNotFoundException, PinIncorrectException,
            ActionNotPossibleException, InvalidCharactersException,
            WeakPinException {
        TokenInfo tokenInfo = getToken(tokenId);

        auditDataHelper.put(tokenInfo);

        possibleActionsRuleEngine.requirePossibleTokenAction(PossibleActionEnum.TOKEN_CHANGE_PIN,
                tokenInfo);
        char[] newPinCharArray = newPin.toCharArray();
        tokenPinValidator.validateSoftwareTokenPin(newPinCharArray);
        try {
            signerRpcClient.updateTokenPin(tokenId, oldPin.toCharArray(), newPinCharArray);
        } catch (SignerException se) {
            if (se.isCausedByTokenNotFound()) {
                throw new TokenNotFoundException(se);
            } else if (se.isCausedByIncorrectPin()) {
                throw new PinIncorrectException(se);
            } else {
                throw se;
            }
        } catch (CodedException ce) {
            throw ce;
        } catch (Exception other) {
            throw new ServiceException(INTERNAL_ERROR, other);
        }
    }

    /**
     * Delete inactive token
     *
     * @param id ID of the token
     */
    public void deleteToken(String id) {
        try {
            TokenInfo tokenInfo = getToken(id);
            auditDataHelper.put(tokenInfo);

            possibleActionsRuleEngine.requirePossibleTokenAction(PossibleActionEnum.TOKEN_DELETE, tokenInfo);
            signerRpcClient.deleteToken(id);
        } catch (TokenNotFoundException e) {
            throw new NotFoundException(TOKEN_NOT_FOUND, e);
        } catch (ActionNotPossibleException e) {
            throw new DataIntegrityException(ACTION_NOT_POSSIBLE, e);
        } catch (CodedException ce) {
            throw ce;
        } catch (Exception other) {
            throw new ServiceException(INTERNAL_ERROR, other);
        }
    }

    public static class PinIncorrectException extends ServiceException {
        public PinIncorrectException(Throwable t) {
            super(TOKEN_PIN_INCORRECT, t);
        }
    }
}

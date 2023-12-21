/*
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.service.SignerNotReachableException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.restapi.util.SecurityHelper;
import org.niis.xroad.securityserver.restapi.facade.SignerProxyFacade;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_AUTH_KEY_REGISTERED_CERT_DETECTED;

/**
 * Service that handles keys
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class KeyService {
    private final SignerProxyFacade signerProxyFacade;
    private final TokenService tokenService;
    private final PossibleActionsRuleEngine possibleActionsRuleEngine;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final SecurityHelper securityHelper;
    private final AuditDataHelper auditDataHelper;
    private final AuditEventHelper auditEventHelper;

    /**
     * Return one key
     * @param keyId
     * @return
     * @throws KeyNotFoundException if key was not found
     */
    public KeyInfo getKey(String keyId) throws KeyNotFoundException {
        Collection<TokenInfo> tokens = tokenService.getAllTokens();
        Optional<KeyInfo> keyInfo = tokens.stream()
                .map(TokenInfo::getKeyInfo)
                .flatMap(List::stream)
                .filter(key -> keyId.equals(key.getId()))
                .findFirst();
        if (!keyInfo.isPresent()) {
            throw new KeyNotFoundException("key with id " + keyId + " not found");
        }

        return keyInfo.get();
    }

    /**
     * Finds matching KeyInfo from this TokenInfo, or throws exception
     * @param tokenInfo token
     * @param keyId id of a key inside the token
     * @throws NoSuchElementException if key with keyId was not found
     */
    public KeyInfo getKey(TokenInfo tokenInfo, String keyId) throws NoSuchElementException {
        return tokenInfo.getKeyInfo().stream()
                .filter(k -> k.getId().equals(keyId))
                .findFirst()
                .get();
    }

    /**
     * Updates key friendly name
     * @throws KeyNotFoundException if key was not found
     * @throws ActionNotPossibleException if friendly name could not be updated for this key
     */
    public KeyInfo updateKeyFriendlyName(String id, String friendlyName) throws KeyNotFoundException,
            ActionNotPossibleException {

        // check that updating friendly name is possible
        TokenInfo tokenInfo = tokenService.getTokenForKeyId(id);

        KeyInfo keyInfo = getKey(tokenInfo, id);
        auditDataHelper.put(RestApiAuditProperty.KEY_ID, keyInfo.getId());
        auditDataHelper.put(RestApiAuditProperty.KEY_FRIENDLY_NAME, friendlyName);
        possibleActionsRuleEngine.requirePossibleKeyAction(PossibleActionEnum.EDIT_FRIENDLY_NAME,
                tokenInfo, keyInfo);

        try {
            signerProxyFacade.setKeyFriendlyName(id, friendlyName);
            keyInfo = getKey(id);
        } catch (KeyNotFoundException e) {
            throw e;
        } catch (CodedException e) {
            if (isCausedByKeyNotFound(e)) {
                throw new KeyNotFoundException(e);
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new SignerNotReachableException("Update key friendly name failed", e);
        }

        return keyInfo;
    }

    /**
     * Generate a new key for selected token
     * @param tokenId
     * @param keyLabel
     * @return {@link KeyInfo}
     * @throws TokenNotFoundException if token was not found
     * @throws ActionNotPossibleException if generate key was not possible for this token
     */
    public KeyInfo addKey(String tokenId, String keyLabel) throws TokenNotFoundException,
            ActionNotPossibleException {

        // check that adding a key is possible
        TokenInfo tokenInfo = tokenService.getToken(tokenId);
        auditDataHelper.put(tokenInfo);
        possibleActionsRuleEngine.requirePossibleTokenAction(PossibleActionEnum.GENERATE_KEY,
                tokenInfo);

        KeyInfo keyInfo = null;
        try {
            keyInfo = signerProxyFacade.generateKey(tokenId, keyLabel);
        } catch (CodedException e) {
            throw e;
        } catch (Exception other) {
            throw new SignerNotReachableException("adding a new key failed", other);
        }
        auditDataHelper.put(RestApiAuditProperty.KEY_ID, keyInfo.getId());
        auditDataHelper.put(RestApiAuditProperty.KEY_LABEL, keyInfo.getLabel());
        auditDataHelper.put(RestApiAuditProperty.KEY_FRIENDLY_NAME, keyInfo.getFriendlyName());
        return keyInfo;
    }

    static boolean isCausedByKeyNotFound(CodedException e) {
        return KEY_NOT_FOUND_FAULT_CODE.equals(e.getFaultCode());
    }

    private static String signerFaultCode(String detail) {
        return SIGNER_X + "." + detail;
    }

    static final String KEY_NOT_FOUND_FAULT_CODE = signerFaultCode(X_KEY_NOT_FOUND);

    /**
     * Deletes one key, and related CSRs and certificates. If the key is an authentication key with a registered
     * certificate, warnings are ignored and certificate is first unregistered, and the key and certificate are
     * deleted after that.
     * @param keyId
     * @throws ActionNotPossibleException if delete was not possible for the key
     * @throws KeyNotFoundException if key with given id was not found
     * @throws GlobalConfOutdatedException if global conf was outdated
     */
    public void deleteKeyAndIgnoreWarnings(String keyId) throws KeyNotFoundException, ActionNotPossibleException,
            GlobalConfOutdatedException {
        try {
            deleteKey(keyId, true);
        } catch (UnhandledWarningsException e) {
            // Since "ignoreWarnings = true", the exception should never be thrown
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes one key, and related CSRs and certificates. If the key is an authentication key with a registered
     * certificate and ignoreWarnings = false, an UnhandledWarningsException is thrown and the key is not deleted. If
     * ignoreWarnings = true, the authentication certificate is first unregistered, and the key and certificate are
     * deleted after that.
     * @param keyId
     * @param ignoreWarnings
     * @throws ActionNotPossibleException if delete was not possible for the key
     * @throws KeyNotFoundException if key with given id was not found
     * @throws GlobalConfOutdatedException if global conf was outdated
     * @throws UnhandledWarningsException if the key is an authentication key, it has a registered certificate,
     * and ignoreWarnings was false
     */
    public void deleteKey(String keyId, Boolean ignoreWarnings) throws KeyNotFoundException, ActionNotPossibleException,
            GlobalConfOutdatedException, UnhandledWarningsException {

        TokenInfo tokenInfo = tokenService.getTokenForKeyId(keyId);
        auditDataHelper.put(tokenInfo);
        KeyInfo keyInfo = getKey(tokenInfo, keyId);
        auditDataHelper.put(keyInfo);

        // verify permissions
        if (keyInfo.getUsage() == null) {
            securityHelper.verifyAuthority("DELETE_KEY");
        } else if (keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION) {
            securityHelper.verifyAuthority("DELETE_AUTH_KEY");
        } else if (keyInfo.getUsage() == KeyUsageInfo.SIGNING) {
            securityHelper.verifyAuthority("DELETE_SIGN_KEY");
        }

        // verify that action is possible
        possibleActionsRuleEngine.requirePossibleKeyAction(PossibleActionEnum.DELETE,
                tokenInfo, keyInfo);

        // unregister possible auth certs
        if (keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION) {
            // get list of auth certs to be unregistered
            List<CertificateInfo> unregister = keyInfo.getCerts().stream().filter(this::shouldUnregister)
                    .collect(Collectors.toList());

            if (!unregister.isEmpty() && !ignoreWarnings) {
                throw new UnhandledWarningsException(
                        new WarningDeviation(WARNING_AUTH_KEY_REGISTERED_CERT_DETECTED, keyId));
            }

            for (CertificateInfo certificateInfo : unregister) {
                unregisterAuthCert(certificateInfo);
            }
        }

        if (!auditDataHelper.dataIsForEvent(RestApiAuditEvent.DELETE_ORPHANS)) {
            auditEventHelper.changeRequestScopedEvent(RestApiAuditEvent.DELETE_KEY_FROM_TOKEN_AND_CONFIG);
        }

        // delete key needs to be done twice. First call deletes the certs & csrs
        try {
            signerProxyFacade.deleteKey(keyId, false);
            signerProxyFacade.deleteKey(keyId, true);
        } catch (CodedException e) {
            throw e;
        } catch (Exception other) {
            throw new SignerNotReachableException("delete key failed", other);
        }
    }

    /**
     * Check if the certificateInfo should be unregistered before it is deleted.
     * @param certificateInfo
     * @return if certificateInfo's status is "REGINPROG" or "REGISTERED" return true, otherwise false
     */
    private boolean shouldUnregister(CertificateInfo certificateInfo) {
        return certificateInfo.getStatus().equals(CertificateInfo.STATUS_REGINPROG)
                || certificateInfo.getStatus().equals(CertificateInfo.STATUS_REGISTERED);
    }

    /**
     * Unregister one auth cert
     */
    private void unregisterAuthCert(CertificateInfo certificateInfo)
            throws GlobalConfOutdatedException {
        // this permission is not checked by unregisterCertificate()
        securityHelper.verifyAuthority("SEND_AUTH_CERT_DEL_REQ");

        // do not use tokenCertificateService.unregisterAuthCert because
        // - it does a bit of extra work to what we need (and makes us do extra work)
        // - we do not want to solve circular dependency KeyService <-> TokenCertificateService

        try {
            // management request to unregister / delete
            managementRequestSenderService.sendAuthCertDeletionRequest(
                    certificateInfo.getCertificateBytes());
            // update status
            signerProxyFacade.setCertStatus(certificateInfo.getId(), CertificateInfo.STATUS_DELINPROG);
        } catch (GlobalConfOutdatedException | CodedException e) {
            throw e;
        } catch (Exception e) {
            throw new SignerNotReachableException("Could not unregister auth cert", e);
        }
    }

    /**
     * Return possible actions for one key
     * @throw KeyNotFoundException if key with given id was not found
     */
    public EnumSet<PossibleActionEnum> getPossibleActionsForKey(String keyId) throws KeyNotFoundException {
        TokenInfo tokenInfo = tokenService.getTokenForKeyId(keyId);
        KeyInfo keyInfo = getKey(tokenInfo, keyId);
        EnumSet<PossibleActionEnum> possibleActions = possibleActionsRuleEngine
                .getPossibleKeyActions(tokenInfo, keyInfo);
        return possibleActions;
    }

}

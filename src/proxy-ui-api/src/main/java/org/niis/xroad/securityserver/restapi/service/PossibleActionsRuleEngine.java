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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.EnumSet;

/**
 * Validation logic for possible actions done on tokens, keys, certs and csrs
 */
@Component
@Slf4j
public class PossibleActionsRuleEngine {

    // duplicate definition, since we dont want add direct dependency on signer
    public static final String SOFTWARE_TOKEN_ID = "0";

    /**
     * Get possible actions for a token
     */
    public EnumSet<PossibleActionEnum> getPossibleTokenActions(TokenInfo tokenInfo) {
        EnumSet<PossibleActionEnum> actions = EnumSet.noneOf(PossibleActionEnum.class);

        if (tokenInfo.isActive()) {
            actions.add(PossibleActionEnum.GENERATE_KEY);
        }

        if (tokenInfo.isActive()) {
            actions.add(PossibleActionEnum.TOKEN_DEACTIVATE);
            actions.add(PossibleActionEnum.TOKEN_CHANGE_PIN);
        } else {
            if (tokenInfo.isAvailable()) {
                actions.add(PossibleActionEnum.TOKEN_ACTIVATE);
            }
        }

        if (tokenInfo.isSavedToConfiguration()) {
            actions.add(PossibleActionEnum.EDIT_FRIENDLY_NAME);
        }

        return actions;
    }

    /**
     * key is "not supported" if it's an auth key inside other than softtoken (id 0)
     */
    public boolean isKeyUnsupported(TokenInfo tokenInfo, KeyInfo keyInfo) {
        return (!SOFTWARE_TOKEN_ID.equals(tokenInfo.getId()))
                && keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION;

    }

    /**
     * Get possible actions for a key
     */
    public EnumSet<PossibleActionEnum> getPossibleKeyActions(TokenInfo tokenInfo,
            KeyInfo keyInfo) {
        EnumSet<PossibleActionEnum> actions = EnumSet.noneOf(PossibleActionEnum.class);

        // DELETE
        boolean keyNotSupported = isKeyUnsupported(tokenInfo, keyInfo);

        // key.js#L26
        // original logic pieces preserved, could be simplified
        if (tokenInfo.isActive()
                && !keyNotSupported
                && !(tokenInfo.isReadOnly() && !keyInfo.isSavedToConfiguration())
                && (keyInfo.isSavedToConfiguration() || tokenInfo.isActive())) {
            actions.add(PossibleActionEnum.DELETE);
        }

        // GENERATE_AUTH_CSR
        // keys.js#35
        if (SOFTWARE_TOKEN_ID.equals(tokenInfo.getId())
                && (keyInfo.getUsage() == null || keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION)
                && !(!keyInfo.isAvailable() || !tokenInfo.isActive() || keyNotSupported)) {
            actions.add(PossibleActionEnum.GENERATE_AUTH_CSR);
        }
        // GENERATE_SIGN_CSR
        if ((keyInfo.getUsage() == null || keyInfo.getUsage() == KeyUsageInfo.SIGNING)
                && !(!keyInfo.isAvailable() || !tokenInfo.isActive() || keyNotSupported)) {
            actions.add(PossibleActionEnum.GENERATE_SIGN_CSR);
        }
        // EDIT_FRIENDLY_NAME
        actions.add(PossibleActionEnum.EDIT_FRIENDLY_NAME);

        return actions;
    }

    /**
     * get possible actions for a certificate
     */
    public EnumSet<PossibleActionEnum> getPossibleCertificateActions(TokenInfo tokenInfo,
            KeyInfo keyInfo,
            CertificateInfo certificateInfo) {
        EnumSet<PossibleActionEnum> actions = EnumSet.noneOf(PossibleActionEnum.class);
        boolean canUnregister = keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION
                && (CertificateInfo.STATUS_REGINPROG.equals(certificateInfo.getStatus())
                || CertificateInfo.STATUS_REGISTERED.equals(certificateInfo.getStatus()));
        if (canUnregister) {
            actions.add(PossibleActionEnum.UNREGISTER);
        }
        boolean savedToConfiguration = certificateInfo.isSavedToConfiguration();
        if (canDeleteCertOrCsr(tokenInfo, savedToConfiguration, canUnregister)) {
            actions.add(PossibleActionEnum.DELETE);
        }
        if (keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION
                && CertificateInfo.STATUS_SAVED.equals(certificateInfo.getStatus())
                && (!canUnregister)) {
            actions.add(PossibleActionEnum.REGISTER);
        }
        if (keyInfo.getUsage() != null && certificateInfo.isSavedToConfiguration()) {
            if (certificateInfo.isActive()) {
                actions.add(PossibleActionEnum.DISABLE);
            } else {
                actions.add(PossibleActionEnum.ACTIVATE);
            }
        }
        if (!certificateInfo.isSavedToConfiguration()) {
            // auth cert cannot be imported
            if (isImportableSignCert(certificateInfo)) {
                actions.add(PossibleActionEnum.IMPORT_FROM_TOKEN);
            }
        }

        return actions;
    }

    /**
     * Find out if certificateInfo is a sign cert that can be imported from a token.
     * If there is an exception while determining this, interpret this as false, "not an importable sign cert"
     * and swallow + log exception
     */
    private boolean isImportableSignCert(CertificateInfo certificateInfo) {
        try {
            X509Certificate x509 = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());
            return CertUtils.isSigningCert(x509);
        } catch (Exception e) {
            log.warn("Unable to determine if certificate is a sign cert -> interpret as false", e);
            return false;
        }
    }

    /**
     * get possible actions for a csr
     */
    public EnumSet<PossibleActionEnum> getPossibleCsrActions(TokenInfo tokenInfo) {
        EnumSet<PossibleActionEnum> actions = EnumSet.noneOf(PossibleActionEnum.class);

        if (canDeleteCertOrCsr(tokenInfo, true, false)) {
            actions.add(PossibleActionEnum.DELETE);
        }
        return actions;
    }

    /**
     * combined logic from keys.js and token_renderer.rb
     */
    private boolean canDeleteCertOrCsr(TokenInfo tokenInfo,
            boolean savedToConfiguration,
            boolean canUnregister) {

        // token_renderer.rb#230
        boolean canDeleteCertFromTokenRenderer;
        if (tokenInfo.isReadOnly() && !savedToConfiguration) {
            canDeleteCertFromTokenRenderer = false;
        } else {
            canDeleteCertFromTokenRenderer = true;
        }

        // keys.js#77
        if (!canUnregister
                && canDeleteCertFromTokenRenderer
                && (savedToConfiguration || tokenInfo.isActive())) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Shortcut helper method for verifying required action
     * @throws ActionNotPossibleException if given action is not in possibleActions
     */
    public void requirePossibleAction(PossibleActionEnum action, EnumSet<PossibleActionEnum> possibleActions)
            throws ActionNotPossibleException {
        if (!possibleActions.contains(action)) {
            throw new ActionNotPossibleException(action + " is not possible");
        }
    }

    /**
     * Shortcut helper method for verifying required action
     * @throws ActionNotPossibleException if given token action is not possible
     */
    public void requirePossibleTokenAction(PossibleActionEnum action, TokenInfo tokenInfo)
            throws ActionNotPossibleException {
        requirePossibleAction(action, ActionTargetType.TOKEN, tokenInfo, null, null, null);
    }

    /**
     * Shortcut helper method for verifying required action
     * @throws ActionNotPossibleException if given key action is not possible
     */
    public void requirePossibleKeyAction(PossibleActionEnum action, TokenInfo tokenInfo, KeyInfo keyInfo)
            throws ActionNotPossibleException {
        requirePossibleAction(action, ActionTargetType.KEY, tokenInfo, keyInfo, null, null);
    }

    /**
     * Shortcut helper method for verifying required action
     * @throws ActionNotPossibleException if given certificate action is not possible
     */
    public void requirePossibleCertificateAction(PossibleActionEnum action, TokenInfo tokenInfo, KeyInfo keyInfo,
            CertificateInfo certificateInfo)
            throws ActionNotPossibleException {
        requirePossibleAction(action, ActionTargetType.CERTIFICATE, tokenInfo, keyInfo, certificateInfo, null);
    }

    /**
     * Shortcut helper method for verifying required action
     * @throws ActionNotPossibleException if given csr action is not possible
     */
    public void requirePossibleCsrAction(PossibleActionEnum action, TokenInfo tokenInfo, KeyInfo keyInfo,
            CertRequestInfo certRequestInfo)
            throws ActionNotPossibleException {
        requirePossibleAction(action, ActionTargetType.CSR, tokenInfo, keyInfo, null, certRequestInfo);
    }

    /**
     * Shortcut helper method for verifying required action
     * @throws ActionNotPossibleException if given action is not possible for give target type
     */
    public void requirePossibleAction(PossibleActionEnum action, ActionTargetType target,
            TokenInfo tokenInfo, KeyInfo keyInfo, CertificateInfo certificateInfo, CertRequestInfo certRequestInfo)
            throws ActionNotPossibleException {
        EnumSet<PossibleActionEnum> possibleActions;
        switch (target) {
            case TOKEN:
                possibleActions = getPossibleTokenActions(tokenInfo);
                break;
            case KEY:
                possibleActions = getPossibleKeyActions(tokenInfo, keyInfo);
                break;
            case CERTIFICATE:
                possibleActions = getPossibleCertificateActions(tokenInfo, keyInfo, certificateInfo);
                break;
            case CSR:
                possibleActions = getPossibleCsrActions(tokenInfo);
                break;
            default:
                throw new IllegalStateException("bad target: " + target);
        }
        if (!possibleActions.contains(action)) {
            throw new ActionNotPossibleException(action + " is not possible");
        }
    }

    /**
     * Enum for supported action targets (token, key, cert, csr)
     */
    public enum ActionTargetType {
        TOKEN,
        KEY,
        CERTIFICATE,
        CSR
    }
}

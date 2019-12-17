/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Logic for possible state changes
 */
@Component
public class StateChangeActionHelper {

    // TO DO: main-level
    public enum StateChangeActionEnum {
        DELETE,
        ACTIVATE,
        DISABLE,
        REGISTER,
        UNREGISTER,
        IMPORT_FROM_TOKEN
    }

    public EnumSet<StateChangeActionEnum> getPossibleTokenActions(TokenInfo tokenInfo) {
        EnumSet<StateChangeActionEnum> actions = EnumSet.noneOf(StateChangeActionEnum.class);
        // not implemented yet
        return actions;
    }

    public EnumSet<StateChangeActionEnum> getPossibleKeyActions(TokenInfo tokenInfo,
            KeyInfo keyInfo) {
        EnumSet<StateChangeActionEnum> actions = EnumSet.noneOf(StateChangeActionEnum.class);
        // not implemented yet
        return actions;
    }

    public EnumSet<StateChangeActionEnum> getPossibleCertificateActions(TokenInfo tokenInfo,
            KeyInfo keyInfo,
            CertificateInfo certificateInfo) {
        EnumSet<StateChangeActionEnum> actions = EnumSet.noneOf(StateChangeActionEnum.class);
        boolean canUnregister = keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION
                && (CertificateInfo.STATUS_REGINPROG.equals(certificateInfo.getStatus())
                || CertificateInfo.STATUS_REGISTERED.equals(certificateInfo.getStatus()));
        if (canUnregister) {
            actions.add(StateChangeActionEnum.UNREGISTER);
        }
        boolean savedToConfiguration = certificateInfo.isSavedToConfiguration();
        if (canDelete(tokenInfo, keyInfo, savedToConfiguration, canUnregister,
                certOrCsrDeletable(tokenInfo, keyInfo, savedToConfiguration))) {
            actions.add(StateChangeActionEnum.DELETE);
        }
        if (keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION
                && CertificateInfo.STATUS_SAVED.equals(certificateInfo.getStatus())
                && (!canUnregister)) {
            actions.add(StateChangeActionEnum.REGISTER);
        }
        if (keyInfo.getUsage() != null && certificateInfo.isSavedToConfiguration()) {
            if (certificateInfo.isActive()) {
                actions.add(StateChangeActionEnum.DISABLE);
            } else {
                actions.add(StateChangeActionEnum.ACTIVATE);
            }
        }
        if (!certificateInfo.isSavedToConfiguration()) {
            actions.add(StateChangeActionEnum.IMPORT_FROM_TOKEN);
        }

        return actions;
    }

    public EnumSet<StateChangeActionEnum> getPossibleCsrActions(TokenInfo tokenInfo,
            KeyInfo keyInfo,
            CertRequestInfo certRequestInfo) {
        EnumSet<StateChangeActionEnum> actions = EnumSet.noneOf(StateChangeActionEnum.class);
        // for csr, savedToConfiguration = always true since
        // token_renderer.rb:
        // :cert_deletable => can_delete_cert?(token, key, true))
        //
        // canUnregister = always false, since
        // token_renderer.rb:
        //         :unregister_enabled => key.usage == KeyUsageInfo::AUTHENTICATION &&
        //          [CertificateInfo::STATUS_REGINPROG,
        //           CertificateInfo::STATUS_REGISTERED].include?(cert.status)
        // (CSR is never in status REGINPROG or REGISTERED)
        if (canDelete(tokenInfo, keyInfo, true, false,
                certOrCsrDeletable(tokenInfo, keyInfo, true))) {
            actions.add(StateChangeActionEnum.DELETE);
        }
        return actions;
    }

    /**
     * from keys.js:
     *             if (cert.cert_deletable &&
     *                 (cert.cert_saved_to_conf || cert.token_active)) {
     *                 $("#delete").enable();
     *             }
     */
    private boolean canDelete(TokenInfo tokenInfo, KeyInfo keyInfo,
            boolean savedToConfiguration,
            boolean canUnregister,
            boolean certOrCsrDeletable) {
        if (!canUnregister
                && certOrCsrDeletable
                && (savedToConfiguration || tokenInfo.isActive())) {
            return true;
        } else {
            return false;
        }
    }

    private boolean certOrCsrDeletable(TokenInfo tokenInfo, KeyInfo keyInfo, boolean savedToConfiguration) {
        boolean canDelete = false;
        if (tokenInfo.isReadOnly() && !savedToConfiguration) {
            canDelete = false;
        } else if (keyInfo.getUsage() == null) {
            canDelete = true;
        } else {
            canDelete = true;
        }
        return canDelete;
    }

    public void requirePossibleAction(StateChangeActionEnum action, EnumSet<StateChangeActionEnum> possibleActions)
            throws TokenCertificateService.ActionNotPossibleException {
        if (!possibleActions.contains(action)) {
            throw new TokenCertificateService.ActionNotPossibleException(action + " is not possible");
        }
    }
}

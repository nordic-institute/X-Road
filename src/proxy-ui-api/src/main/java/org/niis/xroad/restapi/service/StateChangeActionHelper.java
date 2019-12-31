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
 * TO DO: rename, if we are keeping friendly name edit (is not a state change per se)
 * Logic for possible state changes
 */
@Component
public class StateChangeActionHelper {

    public EnumSet<StateChangeActionEnum> getPossibleTokenActions(TokenInfo tokenInfo) {
        EnumSet<StateChangeActionEnum> actions = EnumSet.noneOf(StateChangeActionEnum.class);
        // not implemented yet

        /**
         * keys.js studied 100%
         * application.scss 100%
         * views/keys/key_details 100%
         * views/keys/token_details 100%
         * views/keys/index 100%
         * token_renderer.rb 100%
         * keys_controller.rb 100%
         *
         * * (keys.js#17)
         * -- generate_key: token.active
         *
         *     if ($(".token.row_selected.token-active").length > 0) {
         *         $("#generate_key").enable();
         *     }
         *
         * (keys.js#61) (application.scss #86)
         * -- activate = token.available && !token.active
         * -- deactivate = token.active (not linked to token.available!)
         *
         * token-available =
         * token-active =
         *     $(".token-available .activate_token").enable();
         *     $(".token-unavailable .activate_token").disable();
         * token_activatable => can?(:activate_token),
         * token_locked => token.status == TokenStatusInfo::USER_PIN_LOCKED,
         *
         * -- edit_friendly_name = token_saved_to_configuration (token_details.erb)
         *  * not a state change action as such? does it belong here? TO DO
         *
         *     def token_saved_to_configuration?(token)
         *       token.keyInfo.each do |key|
         *         return true if key_saved_to_configuration?(key)
         *       end
         *
         *       false
         *     end
         *
          */


        return actions;
    }

    public EnumSet<StateChangeActionEnum> getPossibleKeyActions(TokenInfo tokenInfo,
            KeyInfo keyInfo) {
        EnumSet<StateChangeActionEnum> actions = EnumSet.noneOf(StateChangeActionEnum.class);
        // not implemented yet
        return actions;

        /**
         *  (keys.js#21)
         *  -- delete = !key.notSupported && key.deletable && (!key.unsaved || key.active)
         *
         *  key.notSupported = token_id != SOFTTOKEN_ID
         *                     && key_usage == KEY_USAGE_AUTH
         *  key.deletable =
         *        if token.readOnly && !saved_to_conf
         *         return false
         *       end
         *  (also authorization)
         *
         *
         *  key.unsaved = !key_saved_to_conf
         *  key_saved_to_conf = def key_saved_to_configuration?(key)
         *       return true unless key.certRequests.isEmpty
         *
         *       key.certs.each do |cert|
         *         return true if cert.savedToConfiguration
         *       end
         *
         *       false
         *     end
         *
         *  (keys.js#260)
         *
         *  -- generate_auth_csr = keyData.token_id == SOFTTOKEN_ID
         *     && (keyUsage = null || keyUsage = AUTH)
         *     && generate_csr_possible
         *
         *  -- generate_sign_csr = keyData.token_id != SOFTTOKEN_ID
         *     && (keyUsage = null || keyUsage = SIGN)
         *     && generate_csr_possible
         *
         *  (keys.js#35)
         *  generate_csr_possible = !
         *    (key-unavailable || token-inactive || not-supported)
         *
         *          // only softToken with id 0 is allowed to have auth keys
         *         if (!keyUsage && keyData.token_id != SOFTTOKEN_ID) {
         *             keyUsage = KEY_USAGE_SIGN;
         *         }
         *  generate_csr - replacing this with generate_auth_csr and generate_sign_csr that take into
         *  account token type
         *
         *  - edit_friendly_name = key_saved_to_configuration (key_details.erb)
         *  * not a state change action as such? does it belong here? TO DO
         *
         *     def key_saved_to_configuration?(key)
         *       return true unless key.certRequests.isEmpty
         *
         *       key.certs.each do |cert|
         *         return true if cert.savedToConfiguration
         *       end
         *
         *       false
         *     end
         *
         * -- delete =
         *      if (token.readonly && !key.saved_to_conf) => false
         *      else true;
         *
         *     def can_delete_key?(token, key, saved_to_conf)
         *       if token.readOnly && !saved_to_conf
         *         return false
         *       end
         *
         *       if key.usage == KeyUsageInfo::AUTHENTICATION
         *         can?(:delete_auth_key)
         *       elsif key.usage == KeyUsageInfo::SIGNING
         *         can?(:delete_sign_key)
         *       else
         *         can?(:delete_key)
         *       end
         *     end
         *
         *
         *
         */
    }

    /**
     * get possible actions for certificate
     * @param tokenInfo
     * @param keyInfo
     * @param certificateInfo
     * @return
     */
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

    /**
     * get possible actions for csr
     * @param tokenInfo
     * @param keyInfo
     * @param certRequestInfo
     * @return
     */
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
     * TO DO: find better name!
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

    /**
     * TO DO: better name, document where this is from!
     */
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

    /**
     * @throws ActionNotPossibleException if given action is not in possibleActions
     */
    public void requirePossibleAction(StateChangeActionEnum action, EnumSet<StateChangeActionEnum> possibleActions)
            throws ActionNotPossibleException {
        if (!possibleActions.contains(action)) {
            throw new ActionNotPossibleException(action + " is not possible");
        }
    }
}

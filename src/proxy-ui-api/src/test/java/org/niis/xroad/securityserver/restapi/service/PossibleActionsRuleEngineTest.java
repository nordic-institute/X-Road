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

import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PossibleActionsRuleEngineTest extends AbstractServiceTestContext {

    @Autowired
    PossibleActionsRuleEngine possibleActionsRuleEngine;

    @Test
    public void getPossibleCertificateActionUnregister() {
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder().build();

        assertTrue(possibleActionsRuleEngine.getPossibleCertificateActions(tokenInfo,
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .build())
                .contains(PossibleActionEnum.UNREGISTER));
        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(tokenInfo,
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED)
                        .build())
                .contains(PossibleActionEnum.UNREGISTER));
        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(tokenInfo,
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.SIGNING).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .build())
                .contains(PossibleActionEnum.UNREGISTER));
    }

    @Test
    public void getPossibleCertificateActionRegister() {
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder().build();

        assertTrue(possibleActionsRuleEngine.getPossibleCertificateActions(tokenInfo,
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED)
                        .build())
                .contains(PossibleActionEnum.REGISTER));
        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(tokenInfo,
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .build())
                .contains(PossibleActionEnum.REGISTER));
        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(tokenInfo,
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.SIGNING).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED)
                        .build())
                .contains(PossibleActionEnum.REGISTER));
    }

    @Test
    public void getPossibleCertificateActionDelete() {
        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().readOnly(true).build(),
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DELETE));

        assertTrue(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().readOnly(false).build(),
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(null).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DELETE));

        // unreg enabled -> delete not possible
        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().readOnly(false).build(),
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DELETE));

        assertTrue(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().readOnly(false).build(),
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DELETE));

        assertTrue(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().readOnly(false).active(false).build(),
                new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateTestUtils.CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.DELETE));
    }

    @Test
    public void getPossibleCertificateActionDisable() {
        assertTrue(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .active(true)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.DISABLE));

        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .active(false)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.DISABLE));

        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .active(true)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DISABLE));
    }

    @Test
    public void getPossibleCertificateActionActivate() {
        assertTrue(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .active(false)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.ACTIVATE));

        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .active(true)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.ACTIVATE));

        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .active(false)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.ACTIVATE));
    }

    @Test
    public void getPossibleCertificateActionImportFromToken() {
        // non-auth cert, not saved to configuration -> can import
        assertTrue(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.IMPORT_FROM_TOKEN));

        // cert that has been saved to configuration -> can't import
        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.IMPORT_FROM_TOKEN));

        // auth cert -> can't be imported
        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .savedToConfiguration(false)
                        .certificate(CertificateTestUtils.getMockIntermediateCaCertificate())
                        .build())
                .contains(PossibleActionEnum.IMPORT_FROM_TOKEN));

        // cert without X509v3 Key Usage extension -> can't be imported
        assertFalse(possibleActionsRuleEngine.getPossibleCertificateActions(
                new TokenTestUtils.TokenInfoBuilder().build(),
                new TokenTestUtils.KeyInfoBuilder().build(),
                new CertificateTestUtils.CertificateInfoBuilder()
                        .savedToConfiguration(false)
                        .certificate(CertificateTestUtils.getMockCertificateWithoutExtensions())
                        .build())
                .contains(PossibleActionEnum.IMPORT_FROM_TOKEN));
    }

    @Test
    public void getPossibleCsrActionDelete() {
        EnumSet<PossibleActionEnum> actions = possibleActionsRuleEngine.getPossibleCsrActions(
                new TokenTestUtils.TokenInfoBuilder().build());
        assertTrue(actions.contains(PossibleActionEnum.DELETE));
        assertEquals(1, actions.size()); // no other actions

        assertTrue(possibleActionsRuleEngine.getPossibleCsrActions(
                new TokenTestUtils.TokenInfoBuilder().build())
                .contains(PossibleActionEnum.DELETE));
    }

    @Test
    public void requirePossibleAction() throws Exception {
        EnumSet<PossibleActionEnum> actions = EnumSet.of(PossibleActionEnum.ACTIVATE);
        possibleActionsRuleEngine.requirePossibleAction(PossibleActionEnum.ACTIVATE, actions);
        try {
            possibleActionsRuleEngine.requirePossibleAction(PossibleActionEnum.DELETE, actions);
            fail("should throw exception");
        } catch (ActionNotPossibleException expected) {
        }
    }

    @Test
    public void getPossibleTokenActionGenerateKey() {
        assertTrue(possibleActionsRuleEngine.getPossibleTokenActions(
                new TokenTestUtils.TokenInfoBuilder()
                        .active(true)
                        .build())
                .contains(PossibleActionEnum.GENERATE_KEY));

        assertFalse(possibleActionsRuleEngine.getPossibleTokenActions(
                new TokenTestUtils.TokenInfoBuilder()
                        .active(false)
                        .build())
                .contains(PossibleActionEnum.GENERATE_KEY));
    }

    @Test
    public void getPossibleTokenActionActivateOrDeactivate() {
        assertTrue(possibleActionsRuleEngine.getPossibleTokenActions(
                new TokenTestUtils.TokenInfoBuilder()
                        .available(true)
                        .active(false)
                        .build())
                .contains(PossibleActionEnum.TOKEN_ACTIVATE));
        assertFalse(possibleActionsRuleEngine.getPossibleTokenActions(
                new TokenTestUtils.TokenInfoBuilder()
                        .available(true)
                        .active(true)
                        .build())
                .contains(PossibleActionEnum.TOKEN_ACTIVATE));
        assertFalse(possibleActionsRuleEngine.getPossibleTokenActions(
                new TokenTestUtils.TokenInfoBuilder()
                        .available(false)
                        .active(false)
                        .build())
                .contains(PossibleActionEnum.TOKEN_ACTIVATE));

        assertTrue(possibleActionsRuleEngine.getPossibleTokenActions(
                new TokenTestUtils.TokenInfoBuilder()
                        .active(true)
                        .build())
                .contains(PossibleActionEnum.TOKEN_DEACTIVATE));
        assertFalse(possibleActionsRuleEngine.getPossibleTokenActions(
                new TokenTestUtils.TokenInfoBuilder()
                        .active(false)
                        .build())
                .contains(PossibleActionEnum.TOKEN_DEACTIVATE));
        assertTrue(possibleActionsRuleEngine.getPossibleTokenActions(
                new TokenTestUtils.TokenInfoBuilder()
                        .available(false)
                        .active(true)
                        .build())
                .contains(PossibleActionEnum.TOKEN_DEACTIVATE));
    }

    @Test
    public void getPossibleTokenActionEditFriendlyName() {
        TokenInfo unsaved = new TokenTestUtils.TokenInfoBuilder().key(
                new TokenTestUtils.KeyInfoBuilder().cert(
                        new CertificateTestUtils.CertificateInfoBuilder()
                                .savedToConfiguration(false).build())
                        .build()).build();
        TokenInfo saved = new TokenTestUtils.TokenInfoBuilder().key(
                new TokenTestUtils.KeyInfoBuilder().cert(
                        new CertificateTestUtils.CertificateInfoBuilder()
                                .savedToConfiguration(true).build())
                        .build()).build();
        // just check we created test data successfully....
        assertEquals(true, saved.isSavedToConfiguration());
        assertEquals(false, unsaved.isSavedToConfiguration());

        // actual test
        assertTrue(possibleActionsRuleEngine.getPossibleTokenActions(saved)
                .contains(PossibleActionEnum.EDIT_FRIENDLY_NAME));
        assertFalse(possibleActionsRuleEngine.getPossibleTokenActions(unsaved)
                .contains(PossibleActionEnum.EDIT_FRIENDLY_NAME));
    }

    /**
     * Helps when there is only one key. Uses the given token and the single key to request actions.
     */
    private EnumSet<PossibleActionEnum> getPossibleKeyActions(TokenInfo tokenInfo) {
        return possibleActionsRuleEngine.getPossibleKeyActions(tokenInfo,
                tokenInfo.getKeyInfo().iterator().next());
    }

    @Test
    public void getPossibleKeyActionDelete() {
        EnumSet<PossibleActionEnum> actions = getPossibleKeyActions(
                createTestToken(true, false,
                        true, false));
        assertTrue(actions.contains(PossibleActionEnum.DELETE));

        // cant delete unsupported key
        actions = getPossibleKeyActions(
                createTestToken(true, false,
                        true, true));
        assertFalse(actions.contains(PossibleActionEnum.DELETE));

        // cant delete if unsaved and inactive
        actions = getPossibleKeyActions(
                createTestToken(false, false,
                        false, false));
        assertFalse(actions.contains(PossibleActionEnum.DELETE));

        // cant delete if token readonly and key unsaved
        actions = getPossibleKeyActions(
                createTestToken(false, true,
                        true, false));
        assertFalse(actions.contains(PossibleActionEnum.DELETE));

        // cant delete if inactive
        actions = getPossibleKeyActions(
                createTestToken(true, false,
                        false, false));
        assertFalse(actions.contains(PossibleActionEnum.DELETE));

    }

    /**
     * Create a specific token-key combination
     */
    private TokenInfo createTestToken(boolean tokenSaved,
            boolean tokenReadOnly, boolean tokenActive,
            boolean keyNotSupported) {
        CertificateInfo cert = new CertificateTestUtils.CertificateInfoBuilder()
                .savedToConfiguration(tokenSaved)
                .build();
        String tokenId;
        KeyUsageInfo usage;
        if (keyNotSupported) {
            tokenId = PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID + 1;
            usage = KeyUsageInfo.AUTHENTICATION;
        } else {
            tokenId = PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID;
            usage = KeyUsageInfo.AUTHENTICATION;
        }
        KeyInfo key = new TokenTestUtils.KeyInfoBuilder()
                .keyUsageInfo(usage)
                .cert(cert)
                .build();
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .readOnly(tokenReadOnly)
                .active(tokenActive)
                .key(key)
                .id(tokenId)
                .build();
        return tokenInfo;
    }

    @Test
    public void getPossibleKeyActionGenerateAuthCsr() {
        TokenInfo tokenInfo;
        EnumSet<PossibleActionEnum> actions;

        // basic happy case
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)
                .active(true)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // generate is possible is usage = null (undefined)
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)
                .active(true)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(null)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if token is not softtoken
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID + 1)
                .active(true)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if usage = signing
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)
                .active(true)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.SIGNING)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));

        // not possible if key unavailable
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)
                .active(true)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .available(false)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));

        // not possible if token inactive
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)
                .active(false)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));
    }

    @Test
    public void getPossibleKeyActionGenerateSignCsr() {
        TokenInfo tokenInfo;
        EnumSet<PossibleActionEnum> actions;

        // basic happy case
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .active(true)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.SIGNING)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));

        // generate is possible is usage = null (undefined)
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .active(true)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(null)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if usage = auth
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .active(true)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if key unavailable
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .active(true)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.SIGNING)
                        .available(false)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if token inactive
        tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .active(false)
                .key(new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.SIGNING)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));
    }

    @Test
    public void getPossibleKeyActionEditFriendlyName() {
        TokenInfo unsaved = new TokenTestUtils.TokenInfoBuilder().key(
                new TokenTestUtils.KeyInfoBuilder().cert(
                        new CertificateTestUtils.CertificateInfoBuilder()
                                .savedToConfiguration(false).build())
                        .build()).build();
        TokenInfo saved = new TokenTestUtils.TokenInfoBuilder().key(
                new TokenTestUtils.KeyInfoBuilder().cert(
                        new CertificateTestUtils.CertificateInfoBuilder()
                                .savedToConfiguration(true).build())
                        .build()).build();
        EnumSet<PossibleActionEnum> actions = getPossibleKeyActions(saved);
        assertTrue(actions.contains(PossibleActionEnum.EDIT_FRIENDLY_NAME));

        actions = getPossibleKeyActions(unsaved);
        assertTrue(actions.contains(PossibleActionEnum.EDIT_FRIENDLY_NAME));

    }
}


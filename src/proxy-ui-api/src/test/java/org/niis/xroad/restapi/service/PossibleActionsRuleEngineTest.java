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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.util.CertificateTestUtils.CertificateInfoBuilder;
import org.niis.xroad.restapi.util.TokenTestUtils.KeyInfoBuilder;
import org.niis.xroad.restapi.util.TokenTestUtils.TokenInfoBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.niis.xroad.restapi.service.PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class PossibleActionsRuleEngineTest {

    @Autowired
    private PossibleActionsRuleEngine helper;

    @Test
    public void getPossibleCertificateActionUnregister() {
        TokenInfo tokenInfo = new TokenInfoBuilder().build();

        assertTrue(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG).build())
                .contains(PossibleActionEnum.UNREGISTER));
        assertFalse(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED).build())
                .contains(PossibleActionEnum.UNREGISTER));
        assertFalse(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.SIGNING).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG).build())
                .contains(PossibleActionEnum.UNREGISTER));
    }

    @Test
    public void getPossibleCertificateActionRegister() {
        TokenInfo tokenInfo = new TokenInfoBuilder().build();

        assertTrue(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED).build())
                .contains(PossibleActionEnum.REGISTER));
        assertFalse(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG).build())
                .contains(PossibleActionEnum.REGISTER));
        assertFalse(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.SIGNING).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED).build())
                .contains(PossibleActionEnum.REGISTER));
    }

    @Test
    public void getPossibleCertificateActionDelete() {
        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(true).build(),
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DELETE));

        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(false).build(),
                new KeyInfoBuilder().keyUsageInfo(null).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DELETE));

        // unreg enabled -> delete not possible
        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(false).build(),
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DELETE));

        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(false).build(),
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DELETE));

        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(false).active(false).build(),
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.DELETE));
    }

    @Test
    public void getPossibleCertificateActionDisable() {
        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(true)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.DISABLE));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(false)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.DISABLE));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(true)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.DISABLE));
    }

    @Test
    public void getPossibleCertificateActionActivate() {
        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(false)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.ACTIVATE));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(true)
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.ACTIVATE));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(false)
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.ACTIVATE));
    }

    @Test
    public void getPossibleCertificateActionImportFromToken() {
        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .savedToConfiguration(false).build())
                .contains(PossibleActionEnum.IMPORT_FROM_TOKEN));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .savedToConfiguration(true).build())
                .contains(PossibleActionEnum.IMPORT_FROM_TOKEN));
    }

    @Test
    public void getPossibleCsrActionDelete() {
        EnumSet<PossibleActionEnum> actions = helper.getPossibleCsrActions(
                new TokenInfoBuilder().build());
        assertTrue(actions.contains(PossibleActionEnum.DELETE));
        assertEquals(1, actions.size()); // no other actions

        assertTrue(helper.getPossibleCsrActions(
                new TokenInfoBuilder().build())
                .contains(PossibleActionEnum.DELETE));
    }
    @Test
    public void requirePossibleAction() throws Exception {
        EnumSet<PossibleActionEnum> actions = EnumSet.of(PossibleActionEnum.ACTIVATE);
        helper.requirePossibleAction(PossibleActionEnum.ACTIVATE, actions);
        try {
            helper.requirePossibleAction(PossibleActionEnum.DELETE, actions);
            fail("should throw exception");
        } catch (ActionNotPossibleException expected) {
        }
    }

    @Test
    public void getPossibleTokenActionGenerateKey() {
        assertTrue(helper.getPossibleTokenActions(
                new TokenInfoBuilder()
                        .active(true)
                        .build())
                .contains(PossibleActionEnum.GENERATE_KEY));

        assertFalse(helper.getPossibleTokenActions(
                new TokenInfoBuilder()
                        .active(false)
                        .build())
                .contains(PossibleActionEnum.GENERATE_KEY));
    }

    @Test
    public void getPossibleTokenActionActivateOrDeactivate() {
        assertTrue(helper.getPossibleTokenActions(
                new TokenInfoBuilder()
                        .available(true)
                        .active(false)
                        .build())
                .contains(PossibleActionEnum.TOKEN_ACTIVATE));
        assertFalse(helper.getPossibleTokenActions(
                new TokenInfoBuilder()
                        .available(true)
                        .active(true)
                        .build())
                .contains(PossibleActionEnum.TOKEN_ACTIVATE));
        assertFalse(helper.getPossibleTokenActions(
                new TokenInfoBuilder()
                        .available(false)
                        .active(false)
                        .build())
                .contains(PossibleActionEnum.TOKEN_ACTIVATE));

        assertTrue(helper.getPossibleTokenActions(
                new TokenInfoBuilder()
                        .active(true)
                        .build())
                .contains(PossibleActionEnum.TOKEN_DEACTIVATE));
        assertFalse(helper.getPossibleTokenActions(
                new TokenInfoBuilder()
                        .active(false)
                        .build())
                .contains(PossibleActionEnum.TOKEN_DEACTIVATE));
        assertTrue(helper.getPossibleTokenActions(
                new TokenInfoBuilder()
                        .available(false)
                        .active(true)
                        .build())
                .contains(PossibleActionEnum.TOKEN_DEACTIVATE));
    }

    @Test
    public void getPossibleTokenActionEditFriendlyName() {
        TokenInfo unsaved = new TokenInfoBuilder().key(
                new KeyInfoBuilder().cert(
                        new CertificateInfoBuilder()
                                .savedToConfiguration(false).build())
                .build()).build();
        TokenInfo saved = new TokenInfoBuilder().key(
                new KeyInfoBuilder().cert(
                        new CertificateInfoBuilder()
                                .savedToConfiguration(true).build())
                        .build()).build();
        // just check we created test data successfully....
        assertEquals(true, saved.isSavedToConfiguration());
        assertEquals(false, unsaved.isSavedToConfiguration());

        // actual test
        assertTrue(helper.getPossibleTokenActions(saved)
                .contains(PossibleActionEnum.EDIT_FRIENDLY_NAME));
        assertFalse(helper.getPossibleTokenActions(unsaved)
                .contains(PossibleActionEnum.EDIT_FRIENDLY_NAME));
    }

    /**
     * Helps when there is only one key. Uses the given token and the single key to request actions.
     */
    private EnumSet<PossibleActionEnum> getPossibleKeyActions(TokenInfo tokenInfo) {
        return helper.getPossibleKeyActions(tokenInfo,
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
        CertificateInfo cert = new CertificateInfoBuilder()
                .savedToConfiguration(tokenSaved)
                .build();
        String tokenId;
        KeyUsageInfo usage;
        if (keyNotSupported) {
            tokenId = SOFTWARE_TOKEN_ID + 1;
            usage = KeyUsageInfo.AUTHENTICATION;
        } else {
            tokenId = SOFTWARE_TOKEN_ID;
            usage = KeyUsageInfo.AUTHENTICATION;
        }
        KeyInfo key = new KeyInfoBuilder()
                .keyUsageInfo(usage)
                .cert(cert)
                .build();
        TokenInfo tokenInfo = new TokenInfoBuilder()
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
        tokenInfo = new TokenInfoBuilder()
                .id(SOFTWARE_TOKEN_ID)
                .active(true)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // generate is possible is usage = null (undefined)
        tokenInfo = new TokenInfoBuilder()
                .id(SOFTWARE_TOKEN_ID)
                .active(true)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(null)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if token is not softtoken
        tokenInfo = new TokenInfoBuilder()
                .id(SOFTWARE_TOKEN_ID + 1)
                .active(true)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if usage = signing
        tokenInfo = new TokenInfoBuilder()
                .id(SOFTWARE_TOKEN_ID)
                .active(true)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.SIGNING)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));

        // not possible if key unavailable
        tokenInfo = new TokenInfoBuilder()
                .id(SOFTWARE_TOKEN_ID)
                .active(true)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .available(false)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));

        // not possible if token inactive
        tokenInfo = new TokenInfoBuilder()
                .id(SOFTWARE_TOKEN_ID)
                .active(false)
                .key(new KeyInfoBuilder()
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
        tokenInfo = new TokenInfoBuilder()
                .active(true)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.SIGNING)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_AUTH_CSR));

        // generate is possible is usage = null (undefined)
        tokenInfo = new TokenInfoBuilder()
                .active(true)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(null)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertTrue(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if usage = auth
        tokenInfo = new TokenInfoBuilder()
                .active(true)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if key unavailable
        tokenInfo = new TokenInfoBuilder()
                .active(true)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.SIGNING)
                        .available(false)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));

        // not possible if token inactive
        tokenInfo = new TokenInfoBuilder()
                .active(false)
                .key(new KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.SIGNING)
                        .available(true)
                        .build())
                .build();
        actions = getPossibleKeyActions(tokenInfo);
        assertFalse(actions.contains(PossibleActionEnum.GENERATE_SIGN_CSR));
    }

    @Test
    public void getPossibleKeyActionEditFriendlyName() {
        TokenInfo unsaved = new TokenInfoBuilder().key(
                new KeyInfoBuilder().cert(
                        new CertificateInfoBuilder()
                                .savedToConfiguration(false).build())
                        .build()).build();
        TokenInfo saved = new TokenInfoBuilder().key(
                new KeyInfoBuilder().cert(
                        new CertificateInfoBuilder()
                                .savedToConfiguration(true).build())
                        .build()).build();
        EnumSet<PossibleActionEnum> actions = getPossibleKeyActions(saved);
        assertTrue(actions.contains(PossibleActionEnum.EDIT_FRIENDLY_NAME));

        actions = getPossibleKeyActions(unsaved);
        assertFalse(actions.contains(PossibleActionEnum.EDIT_FRIENDLY_NAME));

    }
}


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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.restapi.util.SecurityHelper;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * test key service.
 */
public class KeyServiceTest extends AbstractServiceTestContext {

    @Autowired
    KeyService keyService;

    @Autowired
    AuditDataHelper auditDataHelper;

    @Autowired
    AuditEventHelper auditEventHelper;

    @Autowired
    AuditEventLoggingFacade auditEventLoggingFacade;

    @Autowired
    TokenService tokenService;

    @Autowired
    PossibleActionsRuleEngine possibleActionsRuleEngine;

    @Autowired
    SecurityHelper securityHelper;

    @Autowired
    TokenPinValidator tokenPinValidator;

    // token ids for mocking
    private static final String KEY_NOT_FOUND_KEY_ID = "key-404";
    private static final String AUTH_KEY_ID = "auth-key-id";
    private static final String SIGN_KEY_ID = "sign-key-id";
    private static final String TYPELESS_KEY_ID = "typeless-key-id";
    private static final String REGISTERED_AUTH_CERT_ID = "registered-auth-cert";
    private static final String NONREGISTERED_AUTH_CERT_ID = "unregistered-auth-cert";

    private static final TokenInfo TOKEN_INFO = new TokenTestUtils.TokenInfoBuilder()
            .friendlyName("good-token").build();

    private static final KeyInfo AUTH_KEY = new TokenTestUtils.KeyInfoBuilder()
            .id(AUTH_KEY_ID)
            .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
            .build();

    static {
        // auth key
        CertificateInfo registeredCert = new CertificateTestUtils.CertificateInfoBuilder()
                .savedToConfiguration(true)
                .certificateStatus(CertificateInfo.STATUS_REGISTERED)
                .id(REGISTERED_AUTH_CERT_ID)
                .build();
        CertificateInfo nonregisteredCert = new CertificateTestUtils.CertificateInfoBuilder()
                .savedToConfiguration(true)
                .certificateStatus(CertificateInfo.STATUS_SAVED)
                .id(NONREGISTERED_AUTH_CERT_ID)
                .build();
        AUTH_KEY.getCerts().add(registeredCert);
        AUTH_KEY.getCerts().add(nonregisteredCert);
        CertRequestInfo certRequestInfo = new CertificateTestUtils.CertRequestInfoBuilder()
                .build();
        AUTH_KEY.getCertRequests().add(certRequestInfo);

        // sign and typeless keys
        KeyInfo signKey = new TokenTestUtils.KeyInfoBuilder()
                .id(SIGN_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .build();
        KeyInfo typelessKey = new TokenTestUtils.KeyInfoBuilder()
                .id(TYPELESS_KEY_ID)
                .keyUsageInfo(null)
                .build();
        TOKEN_INFO.getKeyInfo().add(AUTH_KEY);
        TOKEN_INFO.getKeyInfo().add(signKey);
        TOKEN_INFO.getKeyInfo().add(typelessKey);
    }

    @Before
    public void setup() throws Exception {
        doAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            String newKeyName = (String) arguments[1];
            if ("new-friendly-name-update-fails".equals(newKeyName)) {
                throw new CodedException(SIGNER_X + "." + X_KEY_NOT_FOUND);
            }
            if (arguments[0].equals(AUTH_KEY_ID)) {
                ReflectionTestUtils.setField(AUTH_KEY, "friendlyName", newKeyName);
            } else {
                throw new RuntimeException(arguments[0] + " not supported");
            }
            return null;
        }).when(signerProxyFacade).setKeyFriendlyName(any(), any());
        mockPossibleActionsRuleEngineAllowAll();
    }

    @Test
    public void getKey() throws Exception {
        try {
            keyService.getKey(KEY_NOT_FOUND_KEY_ID);
        } catch (KeyNotFoundException expected) {
        }
        KeyInfo keyInfo = keyService.getKey(AUTH_KEY_ID);
        assertEquals(AUTH_KEY_ID, keyInfo.getId());
    }

    @Test
    public void updateKeyFriendlyName() throws Exception {
        KeyInfo keyInfo = keyService.getKey(AUTH_KEY_ID);
        assertEquals("friendly-name", keyInfo.getFriendlyName());
        keyInfo = keyService.updateKeyFriendlyName(AUTH_KEY_ID, "new-friendly-name");
        assertEquals("new-friendly-name", keyInfo.getFriendlyName());
    }

    @Test(expected = KeyNotFoundException.class)
    public void updateKeyFriendlyNameKeyNotExist() throws Exception {
        keyService.updateKeyFriendlyName(KEY_NOT_FOUND_KEY_ID, "new-friendly-name");
    }

    @Test(expected = KeyNotFoundException.class)
    public void updateFriendlyNameUpdatingKeyFails() throws Exception {
        keyService.updateKeyFriendlyName(AUTH_KEY_ID, "new-friendly-name-update-fails");
    }

    @Test
    @WithMockUser(authorities = { "DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY", "SEND_AUTH_CERT_DEL_REQ" })
    public void deleteKey() throws Exception {
        keyService.deleteKeyAndIgnoreWarnings(AUTH_KEY_ID);
        verify(signerProxyFacade, times(1))
                .deleteKey(AUTH_KEY_ID, true);
        verify(signerProxyFacade, times(1))
                .deleteKey(AUTH_KEY_ID, false);
        verify(signerProxyFacade, times(1))
                .setCertStatus(REGISTERED_AUTH_CERT_ID, CertificateInfo.STATUS_DELINPROG);
        verify(managementRequestSenderService, times(1))
                .sendAuthCertDeletionRequest(any());
        verifyNoMoreInteractions(signerProxyFacade);

        try {
            keyService.deleteKeyAndIgnoreWarnings(KEY_NOT_FOUND_KEY_ID);
            fail("should throw exception");
        } catch (KeyNotFoundException expected) {
        }

    }

    @Test
    @WithMockUser(authorities = { "DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY", "SEND_AUTH_CERT_DEL_REQ" })
    public void deleteKeyIgnoreWarningsFalse() throws Exception {
        try {
            keyService.deleteKey(AUTH_KEY_ID, false);
            fail("should throw exception");
        } catch (UnhandledWarningsException expected) {
            Assert.assertEquals(DeviationCodes.WARNING_AUTH_KEY_REGISTERED_CERT_DETECTED,
                    expected.getWarningDeviations().iterator().next().getCode());
        }

    }

    @Test(expected = AccessDeniedException.class)
    // missing SEND_AUTH_CERT_DEL_REQ
    @WithMockUser(authorities = { "DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY" })
    public void deleteKeyUnregisterRequiresSpecificPermission() throws Exception {
        keyService.deleteKeyAndIgnoreWarnings(AUTH_KEY_ID);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_AUTH_KEY", "SEND_AUTH_CERT_DEL_REQ" })
    public void deleteAuthKeyPermissionCheck() throws Exception {
        try {
            keyService.deleteKeyAndIgnoreWarnings(SIGN_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        try {
            keyService.deleteKeyAndIgnoreWarnings(TYPELESS_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        keyService.deleteKeyAndIgnoreWarnings(AUTH_KEY_ID);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_SIGN_KEY" })
    public void deleteSignKeyPermissionCheck() throws Exception {
        try {
            keyService.deleteKeyAndIgnoreWarnings(AUTH_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        try {
            keyService.deleteKeyAndIgnoreWarnings(TYPELESS_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        keyService.deleteKeyAndIgnoreWarnings(SIGN_KEY_ID);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_KEY" })
    public void deleteTypelessKeyPermissionCheck() throws Exception {
        try {
            keyService.deleteKeyAndIgnoreWarnings(AUTH_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        try {
            keyService.deleteKeyAndIgnoreWarnings(SIGN_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        keyService.deleteKeyAndIgnoreWarnings(TYPELESS_KEY_ID);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY" })
    public void deleteChecksPossibleActions() throws Exception {
        mockPossibleActionsRuleEngineDenyAll();
        try {
            keyService.deleteKeyAndIgnoreWarnings(AUTH_KEY_ID);
            fail("should not be possible");
        } catch (ActionNotPossibleException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_KEYS" })
    public void getPossibleActionsForKey() throws Exception {
        EnumSet<PossibleActionEnum> possibleActions = keyService.getPossibleActionsForKey(SIGN_KEY_ID);
        Set<PossibleActionEnum> allActions = new HashSet(Arrays.asList(PossibleActionEnum.values()));
        assertEquals(allActions, new HashSet<>(possibleActions));
    }

    private void mockServices(PossibleActionsRuleEngine possibleActionsRuleEngineParam) {
        // override instead of mocking for better performance
        tokenService = new TokenService(signerProxyFacade, possibleActionsRuleEngineParam, auditDataHelper,
                tokenPinValidator) {
            @Override
            public TokenInfo getTokenForKeyId(String keyId) throws KeyNotFoundException {
                if (AUTH_KEY_ID.equals(keyId)
                        || SIGN_KEY_ID.equals(keyId)
                        || TYPELESS_KEY_ID.equals(keyId)) {
                    return TOKEN_INFO;
                } else {
                    throw new KeyNotFoundException(keyId + " not supported");
                }
            }

            @Override
            public List<TokenInfo> getAllTokens() {
                return Collections.singletonList(TOKEN_INFO);
            }
        };
        keyService = new KeyService(signerProxyFacade, tokenService, possibleActionsRuleEngineParam,
                managementRequestSenderService, securityHelper, auditDataHelper, auditEventHelper);
    }

    private void mockPossibleActionsRuleEngineAllowAll() {
        possibleActionsRuleEngine = new PossibleActionsRuleEngine() {
            @Override
            public EnumSet<PossibleActionEnum> getPossibleKeyActions(TokenInfo tokenInfo,
                    KeyInfo keyInfo) {
                // by default all actions are possible
                return EnumSet.allOf(PossibleActionEnum.class);
            }
        };
        mockServices(possibleActionsRuleEngine);
    }

    private void mockPossibleActionsRuleEngineDenyAll() {
        possibleActionsRuleEngine = new PossibleActionsRuleEngine() {
            @Override
            public EnumSet<PossibleActionEnum> getPossibleKeyActions(TokenInfo tokenInfo,
                    KeyInfo keyInfo) {
                // prepare so that no actions are possible
                return EnumSet.noneOf(PossibleActionEnum.class);
            }

            @Override
            public void requirePossibleKeyAction(PossibleActionEnum action, TokenInfo tokenInfo,
                    KeyInfo keyInfo) throws ActionNotPossibleException {
                throw new ActionNotPossibleException("");
            }
        };
        mockServices(possibleActionsRuleEngine);
    }
}

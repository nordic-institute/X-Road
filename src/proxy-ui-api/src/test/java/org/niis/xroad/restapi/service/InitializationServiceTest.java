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

import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.TokenPinPolicy;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.dto.InitializationStatusDto;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.service.InitializationService.METADATA_PIN_MIN_CHAR_CLASSES;
import static org.niis.xroad.restapi.service.InitializationService.METADATA_PIN_MIN_LENGTH;
import static org.niis.xroad.restapi.service.InitializationService.METADATA_SERVERCONF_EXISTS;
import static org.niis.xroad.restapi.service.InitializationService.METADATA_SOFTWARE_TOKEN_INITIALIZED;
import static org.niis.xroad.restapi.service.InitializationService.WARNING_INIT_SERVER_ID_EXISTS;
import static org.niis.xroad.restapi.service.InitializationService.WARNING_INIT_UNREGISTERED_MEMBER;
import static org.niis.xroad.restapi.service.InitializationService.WeakPinException.WEAK_PIN;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertErrorWithMetadata;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertWarning;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class InitializationServiceTest {
    public static final String INSTANCE = "CS";
    public static final String OWNER_MEMBER_CLASS = "GOV";
    public static final String OWNER_MEMBER_CODE = "M1";
    public static final String SECURITY_SERVER_CODE = "SS3";
    public static final String SOFTWARE_TOKEN_PIN = "1234";
    public static final String SOFTWARE_TOKEN_WEAK_PIN = "a";
    public static final String SOFTWARE_TOKEN_INVALID_PIN = "‘œ‘–ßçıı–ç˛®ç†é®ß";
    public static final String SOFTWARE_TOKEN_VALID_PIN = "TopSecretP1n.";

    private static final ClientId CLIENT = ClientId.create(INSTANCE, OWNER_MEMBER_CLASS,
            OWNER_MEMBER_CODE);
    private static final SecurityServerId SERVER = SecurityServerId.create(INSTANCE, OWNER_MEMBER_CLASS,
            OWNER_MEMBER_CODE, SECURITY_SERVER_CODE);

    @Autowired
    private InitializationService initializationService;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private SystemService systemService;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @MockBean
    private ServerConfService serverConfService;

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    @MockBean
    private ClientService clientService;

    private List<TokenInfo> allTokens;

    @Before
    public void setup() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        when(systemService.isAnchorImported()).thenReturn(true);
        when(serverConfService.isServerConfInitialized()).thenReturn(true);
        when(globalConfFacade.getInstanceIdentifier()).thenReturn("CS");
        when(serverConfService.getOrCreateServerConf()).thenReturn(new ServerConfType());
        when(clientService.getPossiblyManagedEntity(any())).thenReturn(CLIENT);
        initializationService.setTokenPinEnforced(false);
    }

    @Test
    public void isSecurityServerInitialized() {
        InitializationStatusDto initStatus = initializationService.isSecurityServerInitialized();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isInitialized());
    }

    @Test
    public void isSecurityServerInitializedTokenNot() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        InitializationStatusDto initStatus = initializationService.isSecurityServerInitialized();
        assertTrue(initStatus.isAnchorImported());
        assertFalse(initStatus.isInitialized());
    }

    @Test
    public void isSecurityServerInitializedServerConfNot() {
        when(serverConfService.isServerConfInitialized()).thenReturn(false);
        InitializationStatusDto initStatus = initializationService.isSecurityServerInitialized();
        assertTrue(initStatus.isAnchorImported());
        assertFalse(initStatus.isInitialized());
    }

    @Test
    public void isSecurityServerInitializedAnchorNot() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(systemService.isAnchorImported()).thenReturn(false);
        InitializationStatusDto initStatus = initializationService.isSecurityServerInitialized();
        assertFalse(initStatus.isAnchorImported());
        assertFalse(initStatus.isInitialized());
    }

    @Test
    public void initializeSuccess() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerConfInitialized()).thenReturn(false);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, true);
        } catch (Exception e) {
            fail("should not have failed");
        }
    }

    @Test
    public void initializeSuccessWithPinEnforced() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerConfInitialized()).thenReturn(false);
        initializationService.setTokenPinEnforced(true);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_VALID_PIN, true);
        } catch (Exception e) {
            fail("should not have failed");
        }
    }

    @Test
    public void initializeUnknownMember() throws Exception {
        when(globalConfFacade.getMemberName(any())).thenReturn(null);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerConfInitialized()).thenReturn(false);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, false);
            fail("should have failed");
        } catch (UnhandledWarningsException expected) {
            assertWarning(WARNING_INIT_UNREGISTERED_MEMBER, expected, CLIENT.toShortString());
        }
        initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                SOFTWARE_TOKEN_PIN, true);
        assertTrue(true);
    }

    @Test
    public void initializeExistingServerId() throws Exception {
        when(globalConfFacade.getMemberName(any())).thenReturn("Some awesome name. Does not matter really");
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerConfInitialized()).thenReturn(false);
        when(globalConfFacade.existsSecurityServer(any())).thenReturn(true);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, false);
            fail("should have failed");
        } catch (UnhandledWarningsException expected) {
            assertWarning(WARNING_INIT_SERVER_ID_EXISTS, expected, SERVER.toShortString());
        }
        initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                SOFTWARE_TOKEN_PIN, true);
        assertTrue(true);
    }

    @Test
    public void initializeFailPreRequisites() throws Exception {
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, true);
            fail("should have failed");
        } catch (InitializationService.InitializationException expected) {
            assertErrorWithMetadata(InitializationService.InitializationException.INITIALIZATION_FAILED, expected,
                    METADATA_SERVERCONF_EXISTS, METADATA_SOFTWARE_TOKEN_INITIALIZED);
        }
    }

    @Test
    public void initializeFailToken() throws Exception {
        doThrow(new Exception()).when(signerProxyFacade).initSoftwareToken(any());
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerConfInitialized()).thenReturn(false);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, true);
            fail("should have failed");
        } catch (InitializationService.SoftwareTokenInitException expected) {
            // expected
        }
    }

    @Test
    public void initializeFailTokenInvalidPin() throws Exception {
        doThrow(new Exception()).when(signerProxyFacade).initSoftwareToken(any());
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerConfInitialized()).thenReturn(false);
        initializationService.setTokenPinEnforced(true);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_INVALID_PIN, true);
            fail("should have failed");
        } catch (InitializationService.InvalidPinException expected) {
            // expected
        }
    }

    @Test
    public void initializeFailTokenWeakPin() throws Exception {
        doThrow(new Exception()).when(signerProxyFacade).initSoftwareToken(any());
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerConfInitialized()).thenReturn(false);
        initializationService.setTokenPinEnforced(true);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_WEAK_PIN, true);
            fail("should have failed");
        } catch (InitializationService.WeakPinException expected) {
            assertErrorWithMetadata(WEAK_PIN, expected, METADATA_PIN_MIN_LENGTH,
                    String.valueOf(TokenPinPolicy.MIN_PASSWORD_LENGTH),
                    METADATA_PIN_MIN_CHAR_CLASSES,
                    String.valueOf(TokenPinPolicy.MIN_CHARACTER_CLASS_COUNT));
        }
    }
}

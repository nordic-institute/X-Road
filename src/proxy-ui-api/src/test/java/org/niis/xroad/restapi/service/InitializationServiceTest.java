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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.service.InitializationService.ERROR_METADATA_MEMBER_CLASS_BLANK;
import static org.niis.xroad.restapi.service.InitializationService.ERROR_METADATA_PIN_BLANK;
import static org.niis.xroad.restapi.service.InitializationService.METADATA_PIN_MIN_CHAR_CLASSES;
import static org.niis.xroad.restapi.service.InitializationService.METADATA_PIN_MIN_LENGTH;
import static org.niis.xroad.restapi.service.InitializationService.MissingInitParamsException.INIT_PARAMS_MISSING;
import static org.niis.xroad.restapi.service.InitializationService.WARNING_INIT_SERVER_ID_EXISTS;
import static org.niis.xroad.restapi.service.InitializationService.WARNING_INIT_UNREGISTERED_MEMBER;
import static org.niis.xroad.restapi.service.InitializationService.WARNING_SERVERCODE_EXISTS;
import static org.niis.xroad.restapi.service.InitializationService.WARNING_SERVER_OWNER_EXISTS;
import static org.niis.xroad.restapi.service.InitializationService.WARNING_SOFTWARE_TOKEN_INITIALIZED;
import static org.niis.xroad.restapi.service.InitializationService.WeakPinException.WEAK_PIN;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertErrorWithMetadata;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertWarning;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertWarningWithoutMetadata;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class InitializationServiceTest {
    private static final String INSTANCE = "CS";
    private static final String OWNER_MEMBER_CLASS = "GOV";
    private static final String OWNER_MEMBER_CODE = "M1";
    private static final String SECURITY_SERVER_CODE = "SS3";
    private static final String SOFTWARE_TOKEN_PIN = "1234";
    private static final String SOFTWARE_TOKEN_WEAK_PIN = "a";
    private static final String SOFTWARE_TOKEN_INVALID_PIN = "‘œ‘–ßçıı–ç˛®ç†é®ß";
    private static final String SOFTWARE_TOKEN_VALID_PIN = "TopSecretP1n.";
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

    @Before
    public void setup() {
        when(systemService.isAnchorImported()).thenReturn(true);
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        when(globalConfFacade.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(serverConfService.getOrCreateServerConf()).thenReturn(new ServerConfType());
        when(serverConfService.getSecurityServerOwnerId()).thenReturn(CLIENT);
        initializationService.setTokenPinEnforced(false);
    }

    @Test
    public void isSecurityServerInitialized() {
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertTrue(initStatus.isSoftwareTokenInitialized());
    }

    @Test
    public void isSecurityServerInitializedTokenNot() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertFalse(initStatus.isSoftwareTokenInitialized());
    }

    @Test
    public void isSecurityServerInitializedServerOwnerNot() {
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertFalse(initStatus.isServerOwnerInitialized());
        assertTrue(initStatus.isSoftwareTokenInitialized());
    }

    @Test
    public void isSecurityServerInitializedServerCodeNot() {
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertFalse(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertTrue(initStatus.isSoftwareTokenInitialized());
    }

    @Test
    public void isSecurityServerInitializedAnchorNot() {
        when(systemService.isAnchorImported()).thenReturn(false);
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertFalse(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertTrue(initStatus.isSoftwareTokenInitialized());
    }

    @Test
    public void initializeSuccess() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
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
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        initializationService.setTokenPinEnforced(true);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_VALID_PIN, true);
        } catch (Exception e) {
            fail("should not have failed");
        }
    }

    @Test
    public void initializeWarnUnknownMember() throws Exception {
        when(globalConfFacade.getMemberName(any())).thenReturn(null);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
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
    public void initializeWarnExistingServerId() throws Exception {
        when(globalConfFacade.getMemberName(any())).thenReturn("Some awesome name. Does not matter really");
        when(globalConfFacade.existsSecurityServer(any())).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
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
    public void initializeWarnAlreadyInitialized() throws Exception {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, false);
            fail("should have failed");
        } catch (UnhandledWarningsException expected) {
            assertWarningWithoutMetadata(WARNING_SERVER_OWNER_EXISTS, expected);
            assertWarningWithoutMetadata(WARNING_SERVERCODE_EXISTS, expected);
            assertWarningWithoutMetadata(WARNING_SOFTWARE_TOKEN_INITIALIZED, expected);
        }
    }

    @Test
    public void initializeFailPreRequisites() throws Exception {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        try {
            initializationService.initialize(null, null, null, null, true);
            fail("should have failed");
        } catch (InitializationService.MissingInitParamsException expected) {
            assertErrorWithMetadata(INIT_PARAMS_MISSING, expected,
                    InitializationService.ERROR_METADATA_SERVERCODE_BLANK,
                    InitializationService.ERROR_METADATA_MEMBER_CLASS_BLANK,
                    InitializationService.ERROR_METADATA_MEMBER_CODE_BLANK,
                    InitializationService.ERROR_METADATA_PIN_BLANK);
        }
    }

    @Test
    public void initializeFailToken() throws Exception {
        doThrow(new Exception()).when(signerProxyFacade).initSoftwareToken(any());
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
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
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        initializationService.setTokenPinEnforced(true);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_INVALID_PIN, true);
            fail("should have failed");
        } catch (InitializationService.InvalidCharactersException expected) {
            // expected
        }
    }

    @Test
    public void initializeFailTokenWeakPin() throws Exception {
        doThrow(new Exception()).when(signerProxyFacade).initSoftwareToken(any());
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
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

    @Test
    public void initializePartialServerCode() throws Exception {
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                SOFTWARE_TOKEN_PIN, true);
        assertTrue(true);
    }

    @Test
    public void initializePartialServerCodeAndSoftToken() throws Exception {
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        // parts that won't get initialized can be null
        initializationService.initialize(SECURITY_SERVER_CODE, null, null,
                SOFTWARE_TOKEN_PIN, true);
        assertTrue(true);
    }

    @Test
    public void initializePartialFailOwnerAndSoftTokenOwnerMemberClassMissing() throws Exception {
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        // parts that do get initialized cannot be null - such as ownerMemberClass in this test
        try {
            initializationService.initialize(null, null, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, true);
        } catch (InitializationService.MissingInitParamsException expected) {
            assertErrorWithMetadata(INIT_PARAMS_MISSING, expected, ERROR_METADATA_MEMBER_CLASS_BLANK);
        }
    }

    @Test
    public void initializePartialFailServerCodeAndSoftTokenPinMissing() throws Exception {
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    null, true);
        } catch (InitializationService.MissingInitParamsException expected) {
            assertErrorWithMetadata(INIT_PARAMS_MISSING, expected, ERROR_METADATA_PIN_BLANK);
        }
    }
}

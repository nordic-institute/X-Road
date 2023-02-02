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

import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatusDto;
import org.niis.xroad.securityserver.restapi.dto.TokenInitStatusInfo;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.facade.SignerProxyFacade;
import org.niis.xroad.securityserver.restapi.util.DeviationTestUtils;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InitializationServiceTest {
    private static final String INSTANCE = "CS";
    private static final String OWNER_MEMBER_CLASS = "GOV";
    private static final String OWNER_MEMBER_CODE = "M1";
    private static final String SECURITY_SERVER_CODE = "SS3";
    private static final String SOFTWARE_TOKEN_PIN = "1234";
    private static final String SOFTWARE_TOKEN_WEAK_PIN = "a";
    private static final String SOFTWARE_TOKEN_INVALID_PIN = "‘œ‘–ßçıı–ç˛®ç†é®ß";
    private static final String SOFTWARE_TOKEN_VALID_PIN = "TopSecretP1n.";
    private static final ClientId.Conf CLIENT = ClientId.Conf.create(INSTANCE, OWNER_MEMBER_CLASS,
            OWNER_MEMBER_CODE);
    private static final SecurityServerId SERVER = SecurityServerId.Conf.create(INSTANCE, OWNER_MEMBER_CLASS,
            OWNER_MEMBER_CODE, SECURITY_SERVER_CODE);

    @Mock
    private TokenService tokenService;
    @Mock
    private SystemService systemService;
    @Mock
    private ClientService clientService;
    @Mock
    private GlobalConfFacade globalConfFacade;
    @Mock
    private ServerConfService serverConfService;
    @Mock
    private SignerProxyFacade signerProxyFacade;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private TokenPinValidator tokenPinValidator;
    @Mock
    private ExternalProcessRunner externalProcessRunner;

    private InitializationService initializationService;

    @Before
    public void setup() throws ProcessFailedException, InterruptedException, ProcessNotExecutableException {
        when(systemService.isAnchorImported()).thenReturn(true);
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        when(globalConfFacade.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(serverConfService.getOrCreateServerConf()).thenReturn(new ServerConfType());
        when(serverConfService.getSecurityServerOwnerId()).thenReturn(CLIENT);
        when(tokenService.getSoftwareTokenInitStatus()).thenReturn(TokenInitStatusInfo.INITIALIZED);
        when(externalProcessRunner.executeAndThrowOnFailure(any(), any())).thenReturn(
                new ExternalProcessRunner.ProcessResult("mockCmd", 0, new ArrayList<String>()));
        initializationService = new InitializationService(systemService, serverConfService,
                tokenService, globalConfFacade, clientService, signerProxyFacade, auditDataHelper, tokenPinValidator,
                externalProcessRunner);
    }

    @Test
    public void isSecurityServerInitialized() {
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedTokenNot() {
        when(tokenService.getSoftwareTokenInitStatus()).thenReturn(TokenInitStatusInfo.NOT_INITIALIZED);
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.NOT_INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedServerOwnerNot() {
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertFalse(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedServerCodeNot() {
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertFalse(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedAnchorNot() {
        when(systemService.isAnchorImported()).thenReturn(false);
        when(tokenService.getSoftwareTokenInitStatus()).thenReturn(TokenInitStatusInfo.INITIALIZED);
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertFalse(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedSoftwareTokenUnresolved() {
        when(tokenService.getSoftwareTokenInitStatus()).thenReturn(TokenInitStatusInfo.UNKNOWN);
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.UNKNOWN, initStatus.getSoftwareTokenInitStatusInfo());
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
        tokenPinValidator.setTokenPinEnforced(true);
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
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_INIT_UNREGISTERED_MEMBER, expected,
                    CLIENT.toShortString());
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
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_INIT_SERVER_ID_EXISTS,
                    expected, SERVER.toShortString());
        }
        initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                SOFTWARE_TOKEN_PIN, true);
        assertTrue(true);
    }

    @Test
    public void initializeWarnSoftwareTokenAlreadyInitialized() throws Exception {
        when(globalConfFacade.getMemberName(any())).thenReturn("Some awesome name");
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    null, false);
            fail("should have failed");
        } catch (UnhandledWarningsException expected) {
            DeviationTestUtils
                    .assertWarningWithoutMetadata(DeviationCodes.WARNING_SOFTWARE_TOKEN_INITIALIZED, expected);
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
        } catch (InitializationService.InvalidInitParamsException expected) {
            DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, expected,
                    DeviationCodes.ERROR_METADATA_SERVERCODE_NOT_PROVIDED,
                    DeviationCodes.ERROR_METADATA_MEMBER_CLASS_NOT_PROVIDED,
                    DeviationCodes.ERROR_METADATA_MEMBER_CODE_NOT_PROVIDED,
                    DeviationCodes.ERROR_METADATA_PIN_NOT_PROVIDED);
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
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        Mockito.doThrow(InvalidCharactersException.class).when(tokenPinValidator).validateSoftwareTokenPin(any());
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_INVALID_PIN, true);
            fail("should have failed");
        } catch (InvalidCharactersException expected) {
            // expected
        }
    }

    @Test
    public void initializeFailTokenWeakPin() throws Exception {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        Mockito.doThrow(WeakPinException.class).when(tokenPinValidator).validateSoftwareTokenPin(any());
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_WEAK_PIN, true);
            fail("should have failed");
        } catch (WeakPinException expected) {
            // done
        }
    }

    @Test
    public void initializePartialFailRedundantServerCode() throws Exception {
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, true);
            fail("Should have thrown InvalidInitParamsException");
        } catch (InitializationService.InvalidInitParamsException expected) {
            DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, expected,
                    DeviationCodes.ERROR_METADATA_SERVERCODE_EXISTS);
        }
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
        } catch (InitializationService.InvalidInitParamsException expected) {
            DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, expected,
                    DeviationCodes.ERROR_METADATA_MEMBER_CLASS_NOT_PROVIDED);
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
        } catch (InitializationService.InvalidInitParamsException expected) {
            DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, expected,
                    DeviationCodes.ERROR_METADATA_MEMBER_CLASS_EXISTS,
                    DeviationCodes.ERROR_METADATA_MEMBER_CODE_EXISTS, DeviationCodes.ERROR_METADATA_PIN_NOT_PROVIDED);
        }
    }

    @Test
    public void initializePartialFailServerCodeMissingAndSoftTokenPinRedundant() throws Exception {
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        try {
            initializationService.initialize(null, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, true);
        } catch (InitializationService.InvalidInitParamsException expected) {
            DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, expected,
                    DeviationCodes.ERROR_METADATA_SERVERCODE_NOT_PROVIDED,
                    DeviationCodes.ERROR_METADATA_MEMBER_CLASS_EXISTS, DeviationCodes.ERROR_METADATA_MEMBER_CODE_EXISTS,
                    DeviationCodes.ERROR_METADATA_PIN_EXISTS);
        }
    }

    @Test
    public void initializeFailAlreadyFullyInitialized() throws Exception {
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    SOFTWARE_TOKEN_PIN, true);
        } catch (InitializationService.ServerAlreadyFullyInitializedException expected) {
            Assert.assertEquals(DeviationCodes.ERROR_SERVER_ALREADY_FULLY_INITIALIZED,
                    expected.getErrorDeviation().getCode());
        }
    }
}

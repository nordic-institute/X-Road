/*
 * The MIT License
 *
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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatus;
import org.niis.xroad.securityserver.restapi.dto.TokenInitStatusInfo;
import org.niis.xroad.securityserver.restapi.util.DeviationTestUtils;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.signer.client.SignerRpcClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCodes.GPG_KEY_GENERATION_FAILED;
import static org.niis.xroad.securityserver.restapi.util.DeviationTestUtils.assertWarningWithoutMetadata;

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
    private static final ClientIdEntity CLIENT = MemberIdEntity.create(INSTANCE, OWNER_MEMBER_CLASS,
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
    private GlobalConfProvider globalConfProvider;
    @Mock
    private ServerConfService serverConfService;
    @Mock
    private SignerRpcClient signerRpcClient;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private TokenPinValidator tokenPinValidator;
    @Mock
    private SecurityServerBackupService securityServerBackupService;

    private InitializationService initializationService;

    @Before
    public void setup() throws ProcessFailedException, InterruptedException, ProcessNotExecutableException {
        when(systemService.isAnchorImported()).thenReturn(true);
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(serverConfService.getOrCreateServerConfEntity()).thenReturn(new ServerConfEntity());
        when(serverConfService.getSecurityServerOwnerIdEntity()).thenReturn(CLIENT);
        when(tokenService.getSoftwareTokenInitStatus()).thenReturn(TokenInitStatusInfo.INITIALIZED);
        initializationService = new InitializationService(systemService, serverConfService,
                tokenService, globalConfProvider, clientService, signerRpcClient, auditDataHelper, tokenPinValidator,
                securityServerBackupService);
    }

    @Test
    public void isSecurityServerInitialized() {
        InitializationStatus initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedTokenNot() {
        when(tokenService.getSoftwareTokenInitStatus()).thenReturn(TokenInitStatusInfo.NOT_INITIALIZED);
        InitializationStatus initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.NOT_INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedServerOwnerNot() {
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        InitializationStatus initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertFalse(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedServerCodeNot() {
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        InitializationStatus initStatus = initializationService.getSecurityServerInitializationStatus();
        assertTrue(initStatus.isAnchorImported());
        assertFalse(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedAnchorNot() {
        when(systemService.isAnchorImported()).thenReturn(false);
        when(tokenService.getSoftwareTokenInitStatus()).thenReturn(TokenInitStatusInfo.INITIALIZED);
        InitializationStatus initStatus = initializationService.getSecurityServerInitializationStatus();
        assertFalse(initStatus.isAnchorImported());
        assertTrue(initStatus.isServerCodeInitialized());
        assertTrue(initStatus.isServerOwnerInitialized());
        assertEquals(TokenInitStatusInfo.INITIALIZED, initStatus.getSoftwareTokenInitStatusInfo());
    }

    @Test
    public void isSecurityServerInitializedSoftwareTokenUnresolved() {
        when(tokenService.getSoftwareTokenInitStatus()).thenReturn(TokenInitStatusInfo.UNKNOWN);
        InitializationStatus initStatus = initializationService.getSecurityServerInitializationStatus();
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
        when(globalConfProvider.getMemberName(any())).thenReturn(null);
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
        when(globalConfProvider.getMemberName(any())).thenReturn("Some awesome name. Does not matter really");
        when(globalConfProvider.existsSecurityServer(any())).thenReturn(true);
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
    public void initializeWarnSoftwareTokenAlreadyInitialized() {
        when(globalConfProvider.getMemberName(any())).thenReturn("Some awesome name");
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        try {
            initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                    null, false);
            fail("should have failed");
        } catch (UnhandledWarningsException expected) {
            assertWarningWithoutMetadata(DeviationCodes.WARNING_SOFTWARE_TOKEN_INITIALIZED, expected);
        }
    }

    @Test
    public void initializeFailPreRequisites() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        InitializationService.InvalidInitParamsException exception = assertThrows(
                InitializationService.InvalidInitParamsException.class,
                () -> initializationService.initialize(null, null, null, null, true));

        DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, exception,
                DeviationCodes.ERROR_METADATA_SERVERCODE_NOT_PROVIDED,
                DeviationCodes.ERROR_METADATA_MEMBER_CLASS_NOT_PROVIDED,
                DeviationCodes.ERROR_METADATA_MEMBER_CODE_NOT_PROVIDED,
                DeviationCodes.ERROR_METADATA_PIN_NOT_PROVIDED);
    }

    @Test
    public void initializeFailToken() {
        doThrow(XrdRuntimeException.systemException(INTERNAL_ERROR).build()).when(signerRpcClient).initSoftwareToken(any());
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        assertThrows(
                InitializationService.SoftwareTokenInitException.class, () ->
                        initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                                SOFTWARE_TOKEN_PIN, true));
    }

    @Test
    public void initializeGpgKeysGenerationFail() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        doThrow(new CodedException(GPG_KEY_GENERATION_FAILED.code())).when(securityServerBackupService).generateGpgKey(any());

        assertThrows(
                CodedException.class, () ->
                        initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                                SOFTWARE_TOKEN_PIN, true));
    }

    @Test
    public void initializeFailTokenInvalidPin() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        doThrow(InvalidCharactersException.class).when(tokenPinValidator).validateSoftwareTokenPin(any());
        assertThrows(InvalidCharactersException.class, () ->
                initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                        SOFTWARE_TOKEN_INVALID_PIN, true));
    }

    @Test
    public void initializeFailTokenWeakPin() {
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        doThrow(WeakPinException.class).when(tokenPinValidator).validateSoftwareTokenPin(any());
        assertThrows(WeakPinException.class, () ->
                initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                        SOFTWARE_TOKEN_WEAK_PIN, true));
    }

    @Test
    public void initializePartialFailRedundantServerCode() {
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        InitializationService.InvalidInitParamsException exception = assertThrows(
                InitializationService.InvalidInitParamsException.class,
                () -> initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                        SOFTWARE_TOKEN_PIN, true));

        DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, exception,
                DeviationCodes.ERROR_METADATA_SERVERCODE_EXISTS);
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
    public void initializePartialFailOwnerAndSoftTokenOwnerMemberClassMissing() {
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(false);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        // parts that do get initialized cannot be null - such as ownerMemberClass in this test
        InitializationService.InvalidInitParamsException exception = assertThrows(
                InitializationService.InvalidInitParamsException.class,
                () -> initializationService.initialize(null, null, OWNER_MEMBER_CODE,
                        SOFTWARE_TOKEN_PIN, true));

        DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, exception,
                DeviationCodes.ERROR_METADATA_MEMBER_CLASS_NOT_PROVIDED);
    }

    @Test
    public void initializePartialFailServerCodeAndSoftTokenPinMissing() {
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(false);
        InitializationService.InvalidInitParamsException exception = assertThrows(
                InitializationService.InvalidInitParamsException.class,
                () -> initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                        null, true));

        DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, exception,
                DeviationCodes.ERROR_METADATA_MEMBER_CLASS_EXISTS,
                DeviationCodes.ERROR_METADATA_MEMBER_CODE_EXISTS, DeviationCodes.ERROR_METADATA_PIN_NOT_PROVIDED);
    }

    @Test
    public void initializePartialFailServerCodeMissingAndSoftTokenPinRedundant() {
        when(serverConfService.isServerCodeInitialized()).thenReturn(false);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        InitializationService.InvalidInitParamsException exception = assertThrows(
                InitializationService.InvalidInitParamsException.class,
                () -> initializationService.initialize(null, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                        SOFTWARE_TOKEN_PIN, true));

        DeviationTestUtils.assertErrorWithMetadata(DeviationCodes.ERROR_INVALID_INIT_PARAMS, exception,
                DeviationCodes.ERROR_METADATA_SERVERCODE_NOT_PROVIDED,
                DeviationCodes.ERROR_METADATA_MEMBER_CLASS_EXISTS, DeviationCodes.ERROR_METADATA_MEMBER_CODE_EXISTS,
                DeviationCodes.ERROR_METADATA_PIN_EXISTS);
    }

    @Test
    public void initializeFailAlreadyFullyInitialized() {
        when(serverConfService.isServerCodeInitialized()).thenReturn(true);
        when(serverConfService.isServerOwnerInitialized()).thenReturn(true);
        when(tokenService.isSoftwareTokenInitialized()).thenReturn(true);
        InitializationService.ServerAlreadyFullyInitializedException exception = assertThrows(
                InitializationService.ServerAlreadyFullyInitializedException.class,
                () -> initializationService.initialize(SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE,
                        SOFTWARE_TOKEN_PIN, true));

        assertEquals(DeviationCodes.ERROR_SERVER_ALREADY_FULLY_INITIALIZED,
                exception.getErrorDeviation().code());
    }
}

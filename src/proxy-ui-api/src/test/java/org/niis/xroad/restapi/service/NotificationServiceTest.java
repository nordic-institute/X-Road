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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.niis.xroad.restapi.dto.AlertStatus;
import org.niis.xroad.restapi.dto.InitializationStatusDto;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.util.TokenTestUtils;

import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Test NotificationService
 */
@RunWith(MockitoJUnitRunner.class)
public class NotificationServiceTest {
    @Mock
    private GlobalConfFacade globalConfFacade;
    @Mock
    private InitializationService initializationService;
    @Mock
    private TokenService tokenService;

    private NotificationService notificationService;

    private static final String SIGN_TOKEN_ID = "sign-token";

    @Before
    public void setup() {
        InitializationStatusDto initDto = getInitStatus(true);
        when(initializationService.getSecurityServerInitializationStatus()).thenReturn(initDto);
        notificationService = new NotificationService(globalConfFacade, tokenService, initializationService);
    }

    @Test
    public void getAlertsAllOkNoBackupRestore() {
        notificationService.resetBackupRestoreRunningSince();
        assertEquals(null, notificationService.getBackupRestoreRunningSince());

        doAnswer(answer -> null).when(globalConfFacade).verifyValidity();

        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(SignerProxy.SSL_TOKEN_ID)
                .active(true)
                .build();
        List<TokenInfo> allTokens = Collections.singletonList(tokenInfo);

        when(tokenService.getAllTokens()).thenReturn(allTokens);

        AlertStatus alertStatus = notificationService.getAlerts();
        assertEquals(null, alertStatus.getBackupRestoreRunningSince());
        assertEquals(null, alertStatus.getCurrentTime());
        assertEquals(true, alertStatus.getGlobalConfValid());
        assertEquals(true, alertStatus.getSoftTokenPinEntered());
    }

    @Test
    public void getAlertsAllNokBackupRestoreRunning() {
        notificationService.setBackupRestoreRunningSince();
        assertNotNull(null, notificationService.getBackupRestoreRunningSince());

        doThrow(new CodedException("")).when(globalConfFacade).verifyValidity();

        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(SignerProxy.SSL_TOKEN_ID)
                .active(false)
                .build();
        List<TokenInfo> allTokens = Collections.singletonList(tokenInfo);

        when(tokenService.getAllTokens()).thenReturn(allTokens);

        AlertStatus alertStatus = notificationService.getAlerts();
        assertNotNull(alertStatus.getBackupRestoreRunningSince());
        assertNotNull(alertStatus.getCurrentTime());
        assertEquals(false, alertStatus.getGlobalConfValid());
        assertEquals(false, alertStatus.getSoftTokenPinEntered());

        notificationService.resetBackupRestoreRunningSince();
        assertEquals(null, notificationService.getBackupRestoreRunningSince());
    }

    @Test
    public void getAlertsSoftTokenNotFound() {
        notificationService.resetBackupRestoreRunningSince();
        assertEquals(null, notificationService.getBackupRestoreRunningSince());

        doAnswer(answer -> null).when(globalConfFacade).verifyValidity();

        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(SIGN_TOKEN_ID)
                .active(true)
                .build();
        List<TokenInfo> allTokens = Collections.singletonList(tokenInfo);

        when(tokenService.getAllTokens()).thenReturn(allTokens);

        AlertStatus alertStatus = notificationService.getAlerts();
        assertEquals(true, alertStatus.getGlobalConfValid());
        assertEquals(false, alertStatus.getSoftTokenPinEntered());
    }

    @Test
    public void getAlertsNotInitialized() {
        InitializationStatusDto initDto = getInitStatus(false);
        when(initializationService.getSecurityServerInitializationStatus()).thenReturn(initDto);
        AlertStatus alertStatus = notificationService.getAlerts();
        assertFalse(alertStatus.getGlobalConfValid());
        assertFalse(alertStatus.getSoftTokenPinEntered());
    }

    private InitializationStatusDto getInitStatus(boolean isFullyInitialized) {
        InitializationStatusDto initDto = new InitializationStatusDto();
        initDto.setSoftwareTokenInitialized(true);
        initDto.setServerOwnerInitialized(true);
        initDto.setServerCodeInitialized(true);
        initDto.setAnchorImported(isFullyInitialized);
        return initDto;
    }
}

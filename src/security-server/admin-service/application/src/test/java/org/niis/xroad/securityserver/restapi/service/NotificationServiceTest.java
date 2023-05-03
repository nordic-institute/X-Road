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
import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.niis.xroad.securityserver.restapi.dto.AlertStatus;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
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
    private TokenService tokenService;

    private NotificationService notificationService;

    private static final String SIGN_TOKEN_ID = "sign-token";

    @Before
    public void setup() {
        notificationService = new NotificationService(globalConfFacade, tokenService);
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
        assertEquals(true, alertStatus.getGlobalConfValidCheckSuccess());
        assertEquals(true, alertStatus.getSoftTokenPinEntered());
        assertEquals(true, alertStatus.getSoftTokenPinEnteredCheckSuccess());
    }

    @Test
    public void getAlertsAllNokBackupRestoreRunning() {
        notificationService.setBackupRestoreRunningSince();
        assertNotNull(null, notificationService.getBackupRestoreRunningSince());

        doThrow(new CodedException("")).when(globalConfFacade).verifyValidity();

        AlertStatus alertStatus = notificationService.getAlerts();
        assertNotNull(alertStatus.getBackupRestoreRunningSince());
        assertNotNull(alertStatus.getCurrentTime());
        assertEquals(false, alertStatus.getGlobalConfValid());
        assertEquals(true, alertStatus.getGlobalConfValidCheckSuccess());
        assertEquals(false, alertStatus.getSoftTokenPinEntered());
        assertEquals(false, alertStatus.getSoftTokenPinEnteredCheckSuccess());

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
        assertEquals(true, alertStatus.getGlobalConfValidCheckSuccess());
        assertEquals(false, alertStatus.getSoftTokenPinEntered());
        assertEquals(false, alertStatus.getSoftTokenPinEnteredCheckSuccess());
    }

    @Test
    public void getAlertsGlobalConfCheckThrowsRuntimeException() {
        notificationService.resetBackupRestoreRunningSince();
        assertEquals(null, notificationService.getBackupRestoreRunningSince());

        doThrow(new RuntimeException("")).when(globalConfFacade).verifyValidity();

        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(SignerProxy.SSL_TOKEN_ID)
                .active(true)
                .build();
        List<TokenInfo> allTokens = Collections.singletonList(tokenInfo);

        when(tokenService.getAllTokens()).thenReturn(allTokens);

        AlertStatus alertStatus = notificationService.getAlerts();
        assertEquals(null, alertStatus.getBackupRestoreRunningSince());
        assertEquals(null, alertStatus.getCurrentTime());
        assertEquals(false, alertStatus.getGlobalConfValid());
        assertEquals(false, alertStatus.getGlobalConfValidCheckSuccess());
        assertEquals(true, alertStatus.getSoftTokenPinEntered());
        assertEquals(true, alertStatus.getSoftTokenPinEnteredCheckSuccess());
    }
}

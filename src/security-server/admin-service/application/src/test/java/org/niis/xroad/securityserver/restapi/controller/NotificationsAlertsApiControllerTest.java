/*
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
package org.niis.xroad.securityserver.restapi.controller;

import ee.ria.xroad.common.util.TimeUtils;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.domain.AlertData;
import org.niis.xroad.securityserver.restapi.dto.AlertStatus;
import org.niis.xroad.securityserver.restapi.openapi.AbstractApiControllerTestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Test NotificationsAlertsApiController
 */
public class NotificationsAlertsApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    private NotificationsAlertsApiController notificationsAlertsApiController;

    @Test
    @WithMockUser
    public void checkAlerts() {
        AlertStatus alertStatus = new AlertStatus();
        alertStatus.setGlobalConfValid(true);
        alertStatus.setSoftTokenPinEntered(true);

        when(notificationService.getAlerts()).thenReturn(alertStatus);

        ResponseEntity<AlertData> response = notificationsAlertsApiController.checkAlerts();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        AlertData alertData = response.getBody();
        assertEquals(null, alertData.getBackupRestoreRunningSince());
        assertEquals(null, alertData.getCurrentTime());
        assertEquals(alertStatus.getGlobalConfValid(), alertData.getGlobalConfValid());
        assertEquals(alertStatus.getSoftTokenPinEntered(), alertData.getSoftTokenPinEntered());
    }

    @Test
    @WithMockUser
    public void checkAlertsBackupRestoreRunning() {
        OffsetDateTime date = TimeUtils.offsetDateTimeNow(ZoneOffset.UTC);
        AlertStatus alertStatus = new AlertStatus();
        alertStatus.setBackupRestoreRunningSince(date);
        alertStatus.setCurrentTime(date);
        alertStatus.setGlobalConfValid(true);
        alertStatus.setSoftTokenPinEntered(true);

        when(notificationService.getAlerts()).thenReturn(alertStatus);

        ResponseEntity<AlertData> response = notificationsAlertsApiController.checkAlerts();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        AlertData alertData = response.getBody();
        assertEquals(alertStatus.getBackupRestoreRunningSince(), alertData.getBackupRestoreRunningSince());
        assertEquals(alertStatus.getCurrentTime(), alertData.getCurrentTime());
        assertEquals(alertStatus.getGlobalConfValid(), alertData.getGlobalConfValid());
        assertEquals(alertStatus.getSoftTokenPinEntered(), alertData.getSoftTokenPinEntered());
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void resetBackupRestoreRunningSince() {
        ResponseEntity<Void> response = notificationsAlertsApiController.resetBackupRestoreRunningSince();
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @WithMockUser
    public void checkAlertsSoftTokenNotFound() {
        doThrow(new RuntimeException("")).when(notificationService).getAlerts();

        try {
            ResponseEntity<AlertData> response = notificationsAlertsApiController.checkAlerts();
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
            // success
        }
    }
}

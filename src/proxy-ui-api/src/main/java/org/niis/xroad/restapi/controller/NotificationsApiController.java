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
package org.niis.xroad.restapi.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.SessionTimeoutFilter;
import org.niis.xroad.restapi.openapi.v2.converter.AlertDataConverter;
import org.niis.xroad.restapi.domain.AlertData;
import org.niis.xroad.restapi.dto.AlertStatus;
import org.niis.xroad.restapi.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static org.niis.xroad.restapi.openapi.ApiUtil.API_V1_PREFIX;

/**
 * Controller notifications that are polled.
 * Requests to these endpoints do not cause session to stay alive.
 * Session timeout management is implemented by
 * {@link SessionTimeoutFilter}
 */
@RestController
@RequestMapping(NotificationsApiController.NOTIFICATIONS_API_V1_PATH)
@Slf4j
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class NotificationsApiController {
    public static final String NOTIFICATIONS_API_V1_PATH = API_V1_PREFIX + "/notifications";

    private final NotificationService notificationService;
    private final AlertDataConverter alertDataConverter;

    /**
     * check if a HttpSession is currently alive
     */
    @GetMapping(value = "/session-status", produces = { "application/json" })
    public ResponseEntity<StatusData> isSessionAlive(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean isStillAlive = session != null;
        return new ResponseEntity<>(new StatusData(isStillAlive),
                HttpStatus.OK);
    }

    /**
     * check if there are alerts
     */
    @GetMapping(value = "/alerts", produces = { "application/json" })
    public ResponseEntity<AlertData> checkAlerts() {
        AlertStatus alertStatus = notificationService.getAlerts();
        return new ResponseEntity<>(alertDataConverter.convert(alertStatus), HttpStatus.OK);
    }

    /**
     * reset "backupRestoreRunningSince" alert
     */
    @PreAuthorize("hasAnyAuthority('BACKUP_CONFIGURATION', 'RESTORE_CONFIGURATION')")
    @PutMapping(value = "/alerts/reset")
    public ResponseEntity<Void> resetBackupRestoreRunningSince() {
        notificationService.resetBackupRestoreRunningSince();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Data
    @AllArgsConstructor
    private class StatusData {
        private boolean valid;
    }
}

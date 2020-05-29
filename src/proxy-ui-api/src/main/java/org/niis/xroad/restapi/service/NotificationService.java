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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.dto.AlertStatus;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * service class for handling notifications
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class NotificationService {
    private OffsetDateTime backupRestoreRunningSince;
    private final GlobalConfFacade globalConfFacade;
    private final TokenService tokenService;

    /**
     * constructor
     */
    @Autowired
    public NotificationService(GlobalConfFacade globalConfFacade, TokenService tokenService) {
        this.globalConfFacade = globalConfFacade;
        this.tokenService = tokenService;
    }

    /**
     * Checks the status of system alerts that may affect whether the system
     * is operational or not. If alerts are disabled, the status of all alerts true.
     * @return
     */
    public AlertStatus getAlerts() {
        AlertStatus alertStatus = new AlertStatus();
        OffsetDateTime backupRestoreStartedAt = getBackupRestoreRunningSince();
        if (backupRestoreStartedAt != null) {
            alertStatus.setBackupRestoreRunningSince(backupRestoreStartedAt);
            alertStatus.setCurrentTime(OffsetDateTime.now(ZoneOffset.UTC));
        }
        alertStatus.setGlobalConfValid(isGlobalConfValid());
        alertStatus.setSoftTokenPinEntered(isSoftTokenPinEntered());
        return alertStatus;
    }

    /**
     * Verifies that the global configuration is valid.
     * @return
     */
    private boolean isGlobalConfValid() {
        try {
            globalConfFacade.verifyValidity();
            return true;
        } catch (CodedException e) {
            return false;
        }
    }

    /**
     * Checks if soft token pin is entered.
     * @return
     */
    private boolean isSoftTokenPinEntered() {
        Optional<TokenInfo> token = tokenService.getAllTokens().stream()
                .filter(t -> t.getId().equals(SignerProxy.SSL_TOKEN_ID)).findFirst();
        if (!token.isPresent()) {
            throw new RuntimeException("soft token not found");
        }
        return token.get().isActive();
    }

    /**
     * Get date/time since when backup/restore has been running. Returns null if backup/restore is not
     * currently running.
     * @return
     */
    public synchronized OffsetDateTime getBackupRestoreRunningSince() {
        return backupRestoreRunningSince;
    }

    /**
     * Resets backupRestoreRunningSince by setting the value to null.
     */
    public synchronized void resetBackupRestoreRunningSince() {
        backupRestoreRunningSince = null;
    }

    /**
     * Sets backupRestoreRunningSince to current date/time.
     */
    public synchronized void setBackupRestoreRunningSince() {
        backupRestoreRunningSince = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.common.backup.service.BackupRestoreEvent;
import org.niis.xroad.securityserver.restapi.dto.AlertStatus;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * service class for handling notifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private OffsetDateTime backupRestoreRunningSince;
    private final GlobalConfProvider globalConfProvider;
    private final TokenService tokenService;

    /**
     * Checks the status of system alerts that may affect whether the system
     * is operational or not. If backup/restore is running, the status of soft token
     * related alerts is true.
     * @return
     */
    @PreAuthorize("isAuthenticated()")
    public AlertStatus getAlerts() {
        log.debug("checking for alerts");
        AlertStatus alertStatus = new AlertStatus();
        OffsetDateTime backupRestoreStartedAt = getBackupRestoreRunningSince();
        if (backupRestoreStartedAt != null) {
            alertStatus.setBackupRestoreRunningSince(backupRestoreStartedAt);
            alertStatus.setCurrentTime(TimeUtils.offsetDateTimeNow(ZoneOffset.UTC));
            alertStatus.setSoftTokenPinEntered(false);
            alertStatus.setSoftTokenPinEnteredCheckSuccess(false);
        } else {
            try {
                alertStatus.setSoftTokenPinEntered(isSoftTokenPinEntered());
                alertStatus.setSoftTokenPinEnteredCheckSuccess(true);
            } catch (Exception e) {
                log.error("getting soft token pin status failed");
                alertStatus.setSoftTokenPinEntered(false);
                alertStatus.setSoftTokenPinEnteredCheckSuccess(false);
            }
        }

        try {
            alertStatus.setGlobalConfValid(isGlobalConfValid());
            alertStatus.setGlobalConfValidCheckSuccess(true);
        } catch (Exception e) {
            log.error("getting global conf status failed");
            alertStatus.setGlobalConfValid(false);
            alertStatus.setGlobalConfValidCheckSuccess(false);
        }

        List<String> authCertificateIdsWithErrors = getCertificateIdsWithErrors(KeyUsageInfo.AUTHENTICATION);
        if (!authCertificateIdsWithErrors.isEmpty()) {
            alertStatus.setAuthCertificateIdsWithErrors(authCertificateIdsWithErrors);
        }
        List<String> signCertificateIdsWithErrors = getCertificateIdsWithErrors(KeyUsageInfo.SIGNING);
        if (!signCertificateIdsWithErrors.isEmpty()) {
            alertStatus.setSignCertificateIdsWithErrors(signCertificateIdsWithErrors);
        }
        alertStatus.setCertificateRenewalJobSuccess(authCertificateIdsWithErrors.isEmpty() && signCertificateIdsWithErrors.isEmpty());

        return alertStatus;
    }

    /**
     * Verifies that the global configuration is valid.
     * @return
     */
    private boolean isGlobalConfValid() {
        try {
            globalConfProvider.verifyValidity();
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
        if (token.isEmpty()) {
            log.warn("soft token not found");
            throw new RuntimeException("soft token not found");
        }
        return token.get().isActive();
    }

    private List<String> getCertificateIdsWithErrors(KeyUsageInfo keyUsage) {
        return tokenService.getAllTokens().stream()
                .flatMap(t -> t.getKeyInfo().stream())
                .filter(k -> k.getUsage() == keyUsage)
                .flatMap(k -> k.getCerts().stream())
                .filter(c -> c.getStatus().equals(CertificateInfo.STATUS_REGISTERED) && isNotBlank(c.getRenewalError()))
                .map(CertificateInfo::getCertificateDisplayName)
                .toList();
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
    @PreAuthorize("isAuthenticated()")
    public synchronized void resetBackupRestoreRunningSince() {
        backupRestoreRunningSince = null;
    }

    /**
     * Sets backupRestoreRunningSince to current date/time.
     */
    public synchronized void setBackupRestoreRunningSince() {
        backupRestoreRunningSince = TimeUtils.offsetDateTimeNow(ZoneOffset.UTC);
    }

    @PreAuthorize("isAuthenticated()")
    @EventListener
    protected void onEvent(BackupRestoreEvent e) {
        if (BackupRestoreEvent.START.equals(e)) {
            setBackupRestoreRunningSince();
        } else {
            resetBackupRestoreRunningSince();
        }
    }
}

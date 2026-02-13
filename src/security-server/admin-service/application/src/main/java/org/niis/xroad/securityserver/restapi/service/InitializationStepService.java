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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.backupmanager.proto.BackupManagerRpcClient;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatusV2;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatusV2.OverallStatus;
import org.niis.xroad.securityserver.restapi.dto.InitializationStep;
import org.niis.xroad.securityserver.restapi.dto.InitializationStepInfo;
import org.niis.xroad.securityserver.restapi.dto.InitializationStepStatus;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.ANCHOR_NOT_FOUND;

/**
 * Service for managing granular initialization steps with tracking and recovery support.
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class InitializationStepService {

    private static final String LOG_EXECUTING_STEP = "Executing initialization step: {}";
    private static final String LOG_STEP_ALREADY_COMPLETED = "Step {} already completed, skipping";
    private static final String LOG_STEP_COMPLETED = "Step {} completed successfully";

    private final SystemService systemService;
    private final ServerConfService serverConfService;
    private final TokenService tokenService;
    private final GlobalConfProvider globalConfProvider;
    private final ClientService clientService;
    private final SignerRpcClient signerRpcClient;
    private final AuditDataHelper auditDataHelper;
    private final TokenPinValidator tokenPinValidator;
    private final SecurityServerBackupService securityServerBackupService;
    private final EncryptionInitializationService encryptionInitializationService;
    private final VaultClient vaultClient;
    private final BackupManagerRpcClient backupManagerRpcClient;

    /**
     * Get the complete initialization status with granular step tracking.
     */
    public InitializationStatusV2 getInitializationStatus() {
        var anchorImported = systemService.isAnchorImported();

        var steps = new ArrayList<InitializationStepInfo>();
        var pendingSteps = new ArrayList<InitializationStep>();
        var failedSteps = new ArrayList<InitializationStep>();
        var completedSteps = new ArrayList<InitializationStep>();

        for (var step : InitializationStep.values()) {
            var stepInfo = getStepStatus(step);
            steps.add(stepInfo);

            switch (stepInfo.status()) {
                case COMPLETED -> completedSteps.add(step);
                case FAILED -> failedSteps.add(step);
                case NOT_STARTED, IN_PROGRESS, SKIPPED, UNKNOWN -> pendingSteps.add(step);
                default -> log.warn("Unhandled step status: {}", stepInfo.status());
            }
        }

        var overallStatus = determineOverallStatus(anchorImported, completedSteps, failedSteps);
        var fullyInitialized = completedSteps.size() == InitializationStep.values().length;

        return new InitializationStatusV2(
                overallStatus,
                anchorImported,
                steps,
                pendingSteps,
                failedSteps,
                completedSteps,
                fullyInitialized,
                resolveSecurityServerId(),
                resolveTokenPinPolicyEnforced());
    }

    private String resolveSecurityServerId() {
        if (serverConfService.isServerCodeInitialized() && serverConfService.isServerOwnerInitialized()) {
            try {
                var serverId = serverConfService.getSecurityServerId();
                return serverId != null ? serverId.toShortString() : null;
            } catch (Exception e) {
                log.warn("Could not get security server ID", e);
            }
        }
        return null;
    }

    private boolean resolveTokenPinPolicyEnforced() {
        try {
            return signerRpcClient.isEnforcedTokenPinPolicy();
        } catch (Exception e) {
            log.warn("Could not determine token PIN policy enforcement", e);
            return false;
        }
    }

    /**
     * Get the status of a specific initialization step.
     */
    public InitializationStepInfo getStepStatus(InitializationStep step) {
        try {
            boolean completed = isStepCompleted(step);
            if (completed) {
                return InitializationStepInfo.completed(step, Instant.now());
            } else {
                return InitializationStepInfo.notStarted(step);
            }
        } catch (Exception e) {
            log.warn("Error checking status for step {}: {}", step, e.getMessage());
            return new InitializationStepInfo(step, InitializationStepStatus.UNKNOWN,
                    null, null, e.getMessage(), null, true, null);
        }
    }

    private boolean isStepCompleted(InitializationStep step) {
        return switch (step) {
            case SERVERCONF -> serverConfService.isServerCodeInitialized()
                    && serverConfService.isServerOwnerInitialized();
            case SOFTTOKEN -> tokenService.isSoftwareTokenInitialized();
            case GPG_KEY -> isGpgKeyInitialized();
            case MLOG_ENCRYPTION -> isMessageLogEncryptionInitialized();
        };
    }

    private boolean isGpgKeyInitialized() {
        try {
            return backupManagerRpcClient.hasGpgKey();
        } catch (Exception e) {
            log.warn("Could not check GPG key status via RPC, falling back to encryption status: {}", e.getMessage());
            try {
                var encryptionStatus = backupManagerRpcClient.getEncryptionStatus();
                var hasKeys = encryptionStatus.getBackupEncryptionKeys() != null
                        && !encryptionStatus.getBackupEncryptionKeys().isEmpty();
                return encryptionStatus.isBackupEncryptionStatus() || hasKeys;
            } catch (Exception ex) {
                log.warn("Could not check GPG key status: {}", ex.getMessage());
                return false;
            }
        }
    }

    private boolean isMessageLogEncryptionInitialized() {
        try {
            var archivalKeyExists = vaultClient.getMLogArchivalSigningSecretKey().isPresent();
            var dbKeyExists = !vaultClient.getMLogDBEncryptionSecretKeys().isEmpty();
            return archivalKeyExists && dbKeyExists;
        } catch (Exception e) {
            log.warn("Could not check message log encryption status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Execute the SERVERCONF initialization step.
     * Throws if anchor is not imported or if the step fails.
     * Returns completed info if step is already done (idempotent).
     */
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    public synchronized InitializationStepInfo executeServerConfStep(String securityServerCode,
                                                         String ownerMemberClass,
                                                         String ownerMemberCode,
                                                         boolean ignoreWarnings) {
        var step = InitializationStep.SERVERCONF;
        log.info(LOG_EXECUTING_STEP, step);

        if (!systemService.isAnchorImported()) {
            throw new ConflictException("Configuration anchor must be imported first", ANCHOR_NOT_FOUND.build());
        }

        if (isStepCompleted(step)) {
            log.info(LOG_STEP_ALREADY_COMPLETED, step);
            return InitializationStepInfo.completed(step, Instant.now());
        }

        var instanceIdentifier = globalConfProvider.getInstanceIdentifier();
        var ownerClientId = MemberIdEntity.create(instanceIdentifier, ownerMemberClass, ownerMemberCode);

        auditDataHelper.put(OWNER_IDENTIFIER, ownerClientId);
        auditDataHelper.put(SERVER_CODE, securityServerCode);

        createInitialServerConf(ownerClientId, securityServerCode);

        log.info(LOG_STEP_COMPLETED, step);
        return InitializationStepInfo.completed(step, Instant.now());
    }

    /**
     * Execute the SOFTTOKEN initialization step.
     * Throws if prerequisite SERVERCONF is not completed, or if PIN validation fails.
     * Returns completed info if step is already done (idempotent).
     */
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    public synchronized InitializationStepInfo executeSoftTokenStep(String softwareTokenPin) {
        var step = InitializationStep.SOFTTOKEN;
        log.info(LOG_EXECUTING_STEP, step);

        verifyPrerequisite(step);

        if (isStepCompleted(step)) {
            log.info(LOG_STEP_ALREADY_COMPLETED, step);
            return InitializationStepInfo.completed(step, Instant.now());
        }

        var pin = softwareTokenPin.toCharArray();
        tokenPinValidator.validateSoftwareTokenPin(pin);
        signerRpcClient.initSoftwareToken(pin);

        log.info(LOG_STEP_COMPLETED, step);
        return InitializationStepInfo.completed(step, Instant.now());
    }

    /**
     * Execute the GPG_KEY initialization step.
     * Throws if prerequisite SERVERCONF is not completed.
     * Returns completed info if step is already done (idempotent).
     */
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    public synchronized InitializationStepInfo executeGpgKeyStep() {
        var step = InitializationStep.GPG_KEY;
        log.info(LOG_EXECUTING_STEP, step);

        verifyPrerequisite(step);

        if (isStepCompleted(step)) {
            log.info(LOG_STEP_ALREADY_COMPLETED, step);
            return InitializationStepInfo.completed(step, Instant.now());
        }

        var keyRealName = buildKeyName();
        log.info("Generating GPG key pair for {}", keyRealName);
        securityServerBackupService.generateGpgKey(keyRealName);

        log.info(LOG_STEP_COMPLETED, step);
        return InitializationStepInfo.completed(step, Instant.now());
    }

    /**
     * Execute the MLOG_ENCRYPTION initialization step.
     * Throws if prerequisite SERVERCONF is not completed.
     * Returns completed info if step is already done (idempotent).
     */
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    public synchronized InitializationStepInfo executeMessageLogEncryptionStep() {
        var step = InitializationStep.MLOG_ENCRYPTION;
        log.info(LOG_EXECUTING_STEP, step);

        verifyPrerequisite(step);

        if (isStepCompleted(step)) {
            log.info(LOG_STEP_ALREADY_COMPLETED, step);
            return InitializationStepInfo.completed(step, Instant.now());
        }

        var keyRealName = buildKeyName();
        encryptionInitializationService.initializeMessageLogArchivalEncryption(keyRealName);
        encryptionInitializationService.initializeMessageLogDatabaseEncryption();

        log.info(LOG_STEP_COMPLETED, step);
        return InitializationStepInfo.completed(step, Instant.now());
    }

    private void verifyPrerequisite(InitializationStep step) {
        if (!systemService.isAnchorImported()) {
            throw new ConflictException("Configuration anchor must be imported first", ANCHOR_NOT_FOUND.build());
        }
        if (!isStepCompleted(InitializationStep.SERVERCONF)) {
            throw new ConflictException(
                    "SERVERCONF step must be completed before " + step,
                    new org.niis.xroad.common.core.exception.ErrorDeviation("prerequisite_not_met"));
        }
    }

    private String buildKeyName() {
        var serverId = serverConfService.getSecurityServerId();
        return serverId.getXRoadInstance() + "/" + serverId.getMemberClass() + "/"
                + serverId.getMemberCode() + "/" + serverId.getServerCode();
    }

    private void createInitialServerConf(ClientIdEntity ownerClientId, String securityServerCode) {
        var serverConfEntity = serverConfService.getOrCreateServerConfEntity();

        if (serverConfEntity.getServerCode() == null || serverConfEntity.getServerCode().isEmpty()) {
            serverConfEntity.setServerCode(securityServerCode);
        }

        if (serverConfEntity.getOwner() == null) {
            var ownerClient = clientService.getLocalClientEntity(ownerClientId);
            if (ownerClient == null) {
                ownerClient = clientService.addClient(ownerClientId, serverConfEntity,
                        org.niis.xroad.serverconf.IsAuthentication.SSLAUTH,
                        org.niis.xroad.serverconf.model.Client.STATUS_SAVED);
            }
            serverConfEntity.setOwner(ownerClient);
        }
    }

    private OverallStatus determineOverallStatus(boolean anchorImported,
                                                  List<InitializationStep> completedSteps,
                                                  List<InitializationStep> failedSteps) {
        if (!anchorImported) {
            return OverallStatus.NOT_STARTED;
        }

        int totalSteps = InitializationStep.values().length;

        if (completedSteps.size() == totalSteps) {
            return OverallStatus.COMPLETED;
        }

        if (!failedSteps.isEmpty()) {
            if (failedSteps.contains(InitializationStep.SERVERCONF)) {
                return OverallStatus.FAILED;
            }
            return OverallStatus.PARTIALLY_COMPLETED;
        }

        if (completedSteps.isEmpty()) {
            return OverallStatus.NOT_STARTED;
        }

        return OverallStatus.PARTIALLY_COMPLETED;
    }

}

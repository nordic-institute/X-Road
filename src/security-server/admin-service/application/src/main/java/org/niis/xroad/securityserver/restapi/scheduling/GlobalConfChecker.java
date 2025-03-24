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
package org.niis.xroad.securityserver.restapi.scheduling;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.restapi.common.backup.service.BackupRestoreEvent;
import org.niis.xroad.securityserver.restapi.cache.SecurityServerAddressChangeStatus;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.niis.xroad.serverconf.entity.ClientEntity;
import org.niis.xroad.serverconf.entity.ServerConfEntity;
import org.niis.xroad.serverconf.mapper.TimestampingServiceMapper;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.TimestampingService;
import org.niis.xroad.signer.api.dto.AuthKeyInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.SystemProperties.NodeType.SLAVE;

/**
 * Job that checks whether globalconf has changed.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GlobalConfChecker {
    public static final int JOB_REPEAT_INTERVAL_MS = 30000;
    public static final int INITIAL_DELAY_MS = 30000;
    private volatile boolean restoreInProgress = false;
    private final GlobalConfCheckerHelper globalConfCheckerHelper;
    private final GlobalConfProvider globalConfProvider;
    private final SignerRpcClient signerRpcClient;
    private final SecurityServerAddressChangeStatus addressChangeStatus;
    private final MailNotificationHelper mailNotificationHelper;

    /**
     * Reloads global configuration, and updates client statuses, authentication certificate statuses
     * and server owner identity to the serverconf database. The task is scheduled at a fixed rate
     * which means that the task is run at a fixed interval (defined by FIXED_RATE_MS) regardless of the
     * previous executions of the task. However, scheduled tasks do not run in parallel by default. The
     * next task won't be invoked until the previous one is done. Set an initial delay before running the task
     * for the first time after a startup to be sure that all required components are available, e.g.
     * SignerClient may not be available immediately.
     *
     * @throws Exception
     */
    @Scheduled(fixedRate = JOB_REPEAT_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    @Transactional
    public void checkGlobalConf() {
        if (restoreInProgress) {
            log.debug("Backup restore in progress - skipping globalconf update");
            return;
        }

        try {
            log.debug("Check globalconf for updates");
            reloadGlobalConf();
            updateServerConf();
        } catch (Exception e) {
            log.error("Checking globalconf for updates failed", e);
        }
    }

    @EventListener
    protected void onEvent(BackupRestoreEvent e) {
        restoreInProgress = BackupRestoreEvent.START.equals(e);
    }

    private void reloadGlobalConf() {
        // conf MUST be reloaded before checking validity otherwise expired or invalid conf is never reloaded
        log.debug("Reloading globalconf");
        globalConfProvider.reload();
        globalConfProvider.verifyValidity();
    }

    private void updateServerConf() {
        // In clustered setup slave nodes may skip serverconf updates
        if (SLAVE.equals(SystemProperties.getServerNodeType())) {
            log.debug("This is a slave node - skip serverconf updates");
            return;
        }

        ServerConfEntity serverConf = globalConfCheckerHelper.getServerConf();

        addressChangeStatus.getAddressChangeRequest()
                .ifPresent(requestedAddress -> {
                    var currentAddress = globalConfProvider.getSecurityServerAddress(buildSecurityServerId(serverConf));
                    if (requestedAddress.equals(currentAddress)) {
                        addressChangeStatus.clear();
                    }
                });

        try {
            if (globalConfProvider.getServerOwner(buildSecurityServerId(serverConf)) == null) {
                log.debug("Server owner not found in globalconf - owner may have changed");
                updateOwner(serverConf);
            }
            SecurityServerId securityServerId = buildSecurityServerId(serverConf);
            log.debug("Security Server ID is \"{}\"", securityServerId);
            updateClientStatuses(serverConf, securityServerId);
            updateAuthCertStatuses(securityServerId);
            if (SystemProperties.geUpdateTimestampServiceUrlsAutomatically()) {
                updateTimestampServiceUrls(globalConfProvider.getApprovedTsps(
                                globalConfProvider.getInstanceIdentifier()),
                        TimestampingServiceMapper.get().toTargets(serverConf.getTimestampingServices())
                );
            }
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    /**
     * Matches timestamping services in globalTsps with localTsps by name and checks if the URLs have changed.
     * If the change is unambiguous, it's performed on localTsps. Otherwise a warning is logged.
     *
     * @param globalTsps timestamping services from global configuration
     * @param localTsps  timestamping services from local database
     */
    void updateTimestampServiceUrls(List<SharedParameters.ApprovedTSA> globalTsps, List<TimestampingService> localTsps) {

        for (TimestampingService localTsp : localTsps) {
            List<SharedParameters.ApprovedTSA> globalTspMatches = globalTsps.stream()
                    .filter(g -> g.getName().equals(localTsp.getName()))
                    .toList();
            if (globalTspMatches.size() > 1) {
                Optional<SharedParameters.ApprovedTSA> urlChanges =
                        globalTspMatches.stream().filter(t -> !t.getUrl().equals(localTsp.getUrl())).findAny();
                if (urlChanges.isPresent()) {
                    log.warn("Skipping timestamping service URL update due to multiple services with the same name: {}",
                            globalTspMatches.get(0).getName());
                }
            } else if (globalTspMatches.size() == 1) {
                SharedParameters.ApprovedTSA globalTspMatch = globalTspMatches.get(0);
                if (!globalTspMatch.getUrl().equals(localTsp.getUrl())) {
                    log.info("Timestamping service URL has changed in the global configuration. "
                                    + "Updating the changes to the local configuration, Name: {}, Old URL: {}, New URL: {}",
                            localTsp.getName(), localTsp.getUrl(), globalTspMatch.getUrl());
                    localTsp.setUrl(globalTspMatch.getUrl());
                }
            }
        }
    }

    private SecurityServerId buildSecurityServerId(ClientId ownerId, String serverCode) {
        return SecurityServerId.Conf.create(
                ownerId.getXRoadInstance(), ownerId.getMemberClass(),
                ownerId.getMemberCode(), serverCode);
    }

    private SecurityServerId buildSecurityServerId(ServerConfEntity serverConf) {
        ClientId ownerId = serverConf.getOwner().getIdentifier();
        return buildSecurityServerId(ownerId, serverConf.getServerCode());
    }

    private void updateOwner(ServerConfEntity serverConf) throws Exception {
        ClientId ownerId = serverConf.getOwner().getIdentifier();
        for (ClientEntity client : serverConf.getClients()) {
            // Look for another member that is not the owner
            if (client.getIdentifier().getSubsystemCode() == null
                    && !client.getIdentifier().equals(ownerId)) {
                log.debug("Found potential new owner: \"{}\"", client.getIdentifier());

                // Build a new server id using the alternative member as owner
                SecurityServerId altSecurityServerId = buildSecurityServerId(client.getIdentifier(),
                        serverConf.getServerCode());

                // Get local auth cert
                X509Certificate cert = getAuthCert(altSecurityServerId);

                // Does the alternative server id exist in global conf?
                // And does the local auth cert match with the auth cert of
                // the alternative server from global conf?
                if (globalConfProvider.getServerOwner(altSecurityServerId) != null
                        && cert != null
                        && altSecurityServerId.equals(globalConfProvider.getServerId(cert))
                ) {
                    log.debug("Set \"{}\" as new owner", client.getIdentifier());
                    serverConf.setOwner(client);
                }
            }
        }
    }

    private X509Certificate getAuthCert(SecurityServerId serverId) throws Exception {
        log.debug("Get auth cert for security server '{}'", serverId);

        AuthKeyInfo keyInfo = signerRpcClient.getAuthKey(serverId);
        if (keyInfo != null && keyInfo.getCert() != null) {
            return CryptoUtils.readCertificate(keyInfo.getCert().getCertificateBytes());
        }
        log.warn("Failed to read authentication key");
        return null;
    }

    private void updateClientStatuses(ServerConfEntity serverConf, SecurityServerId securityServerId) {
        log.debug("Updating client statuses");

        for (ClientEntity client : serverConf.getClients()) {
            boolean registered = globalConfProvider.isSecurityServerClient(
                    client.getIdentifier(), securityServerId);

            log.debug("Client '{}' registered = '{}'", client.getIdentifier(),
                    registered);

            if (registered && client.getClientStatus() != null) {
                switch (client.getClientStatus()) {
                    case Client.STATUS_REGISTERED:
                        // do nothing
                        break;
                    case Client.STATUS_SAVED,
                         Client.STATUS_REGINPROG,
                         Client.STATUS_GLOBALERR,
                         Client.STATUS_ENABLING_INPROG:
                        updateClientStatus(client, Client.STATUS_REGISTERED);
                        break;
                    default:
                        log.warn("Unexpected status {} for client '{}'",
                                client.getIdentifier(),
                                client.getClientStatus());
                }
            }

            if (!registered && Client.STATUS_REGISTERED.equals(client.getClientStatus())) {
                updateClientStatus(client, Client.STATUS_GLOBALERR);
            }

            if (!registered && Client.STATUS_DISABLING_INPROG.equals(client.getClientStatus())) {
                updateClientStatus(client, Client.STATUS_DISABLED);
            }
        }
    }

    private void updateClientStatus(ClientEntity client, String status) {
        client.setClientStatus(status);
        log.debug("Setting client '{}' status to '{}'", client.getIdentifier(), client.getClientStatus());
    }

    private void updateAuthCertStatuses(SecurityServerId securityServerId) {
        log.debug("Updating auth cert statuses");

        signerRpcClient.getTokens().stream().flatMap(t -> t.getKeyInfo().stream())
                .filter(k -> KeyUsageInfo.AUTHENTICATION.equals(k.getUsage()))
                .forEach(keyInfo -> updateCertStatuses(securityServerId, keyInfo));
    }

    private void updateCertStatuses(SecurityServerId securityServerId, KeyInfo keyInfo) {
        for (CertificateInfo certInfo : keyInfo.getCerts()) {
            try {
                updateCertStatus(securityServerId, certInfo, keyInfo.getUsage());
            } catch (SignerException se) {
                throw se;
            } catch (Exception e) {
                throw translateException(e);
            }
        }
    }

    private void updateCertStatus(SecurityServerId securityServerId, CertificateInfo certInfo, KeyUsageInfo keyUsageInfo) throws Exception {
        X509Certificate cert = CryptoUtils.readCertificate(certInfo.getCertificateBytes());

        boolean registered = securityServerId.equals(globalConfProvider.getServerId(cert));

        if (registered && certInfo.getStatus() != null) {
            switch (certInfo.getStatus()) {
                case CertificateInfo.STATUS_REGISTERED -> {
                    // do nothing
                }
                case CertificateInfo.STATUS_REGINPROG -> {
                    setCertStatus(cert, CertificateInfo.STATUS_REGISTERED, certInfo);
                    mailNotificationHelper.sendAuthCertRegisteredNotification(securityServerId, certInfo);
                    activateCert(certInfo, cert, keyUsageInfo, securityServerId);
                }
                case CertificateInfo.STATUS_SAVED, CertificateInfo.STATUS_GLOBALERR ->
                        setCertStatus(cert, CertificateInfo.STATUS_REGISTERED, certInfo);
                default -> log.warn("Unexpected status '{}' for certificate '{}'",
                        certInfo.getStatus(), CertUtils.identify(cert));
            }

        }

        if (!registered && CertificateInfo.STATUS_REGISTERED.equals(certInfo.getStatus())) {
            setCertStatus(cert, CertificateInfo.STATUS_GLOBALERR, certInfo);
        }
    }

    private void activateCert(CertificateInfo certInfo,
                              X509Certificate cert,
                              KeyUsageInfo keyUsageInfo,
                              SecurityServerId securityServerId) throws IOException, OperatorCreationException {
        if (SystemProperties.getAutomaticActivateAuthCertificate()) {
            log.debug("Activating certificate '{}'", CertUtils.identify(cert));
            String ownerMemberId = securityServerId.getOwner().asEncodedId();
            try {
                signerRpcClient.activateCert(certInfo.getId());
                mailNotificationHelper.sendCertActivatedNotification(ownerMemberId, securityServerId, certInfo, keyUsageInfo);
            } catch (SignerException e) {
                String certHash = CryptoUtils.calculateCertHexHash(certInfo.getCertificateBytes());
                CertificateInfo updatedCertInfo = signerRpcClient.getCertForHash(certHash);
                mailNotificationHelper.sendCertActivationFailureNotification(ownerMemberId,
                        updatedCertInfo.getCertificateDisplayName(),
                        (SecurityServerId.Conf) securityServerId,
                        keyUsageInfo,
                        updatedCertInfo.getOcspVerifyBeforeActivationError());
            }
        }
    }

    private void setCertStatus(X509Certificate cert, String status, CertificateInfo certInfo) throws Exception {
        log.debug("Setting certificate '{}' status to '{}'", CertUtils.identify(cert), status);
        signerRpcClient.setCertStatus(certInfo.getId(), status);
    }
}

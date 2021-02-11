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
package org.niis.xroad.restapi.scheduling;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.ApprovedTSAType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.signer.protocol.dto.AuthKeyInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.GetAuthKey;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.service.BackupRestoreEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.SystemProperties.NodeType.SLAVE;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Job that checks whether globalconf has changed.
 */
@Component
@Slf4j
public class GlobalConfChecker {
    public static final int JOB_REPEAT_INTERVAL_MS = 30000;
    public static final int INITIAL_DELAY_MS = 30000;
    private final GlobalConfCheckerHelper globalConfCheckerHelper;
    private final GlobalConfFacade globalConfFacade;
    private final SignerProxyFacade signerProxyFacade;
    private volatile boolean restoreInProgress = false;

    @Autowired
    public GlobalConfChecker(GlobalConfCheckerHelper globalConfCheckerHelper, GlobalConfFacade globalConfFacade,
            SignerProxyFacade signerProxyFacade) {
        this.globalConfCheckerHelper = globalConfCheckerHelper;
        this.globalConfFacade = globalConfFacade;
        this.signerProxyFacade = signerProxyFacade;
    }

    /**
     * Reloads global configuration, and updates client statuses, authentication certificate statuses
     * and server owner identity to the serverconf database. The task is scheduled at a fixed rate
     * which means that the task is run at a fixed interval (defined by FIXED_RATE_MS) regardless of the
     * previous executions of the task. However, scheduled tasks do not run in parallel by default. The
     * next task won't be invoked until the previous one is done. Set an initial delay before running the task
     * for the first time after a startup to be sure that all required components are available, e.g.
     * SignerClient may not be available immediately.
     * @throws Exception
     */
    @Scheduled(fixedRate = JOB_REPEAT_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    @Transactional
    public void updateServerConf() {
        // In clustered setup slave nodes may skip globalconf updates
        if (SLAVE.equals(SystemProperties.getServerNodeType())) {
            log.debug("This is a slave node - skip globalconf updates");
            return;
        }

        if (restoreInProgress) {
            log.debug("Backup restore in progress - skipping globalconf update");
            return;
        }

        try {
            log.debug("Check globalconf for updates");
            checkGlobalConf();
        } catch (Exception e) {
            log.error("Checking globalconf for updates failed", e);
            throw e;
        }
    }

    @EventListener
    protected void onEvent(BackupRestoreEvent e) {
        restoreInProgress = BackupRestoreEvent.START.equals(e);
    }

    private void checkGlobalConf() {
        globalConfFacade.verifyValidity();

        log.debug("Reloading globalconf");
        globalConfFacade.reload(); // XXX: temporary fix

        ServerConfType serverConf = globalConfCheckerHelper.getServerConf();
        SecurityServerId securityServerId = null;

        try {
            if (globalConfFacade.getServerOwner(buildSecurityServerId(serverConf)) == null) {
                log.debug("Server owner not found in globalconf - owner may have changed");
                updateOwner(serverConf);
            }
            securityServerId = buildSecurityServerId(serverConf);
            log.debug("Security Server ID is \"{}\"", securityServerId);
            updateClientStatuses(serverConf, securityServerId);
            updateAuthCertStatuses(securityServerId);
        } catch (Exception e) {
            throw translateException(e);
        }

        if (SystemProperties.getAutoUpdateTimestampServiceUrl()) {
            log.debug("proxy-ui-api.auto-update-timestamp-service-url setting is enabled");
            updateTimestampServiceUrls(serverConf);
        } else {
            log.debug("proxy-ui-api.auto-update-timestamp-service-url setting is disabled");
        }
    }

    private void updateTimestampServiceUrls(ServerConfType serverConf) {
        List<TspType> tsp = serverConf.getTsp();
        log.debug("List timestamping services in serverconf");
        tsp.stream().forEach(t -> log.debug("Name: {} URL: {}", t.getName(), t.getUrl()));

        log.debug("Global conf instance: {}", globalConfFacade.getInstanceIdentifier());
        log.debug("List timestamping services in global conf");
        List<ApprovedTSAType> approvedTspTypes = globalConfFacade
                .getApprovedTspTypes(globalConfFacade.getInstanceIdentifier());
        approvedTspTypes.stream().forEach(t -> log.debug("Name: {} URL: {}", t.getName(), t.getUrl()));

        for (int i = 0; i < tsp.size(); i++) {
            TspType serverConfTsp = tsp.get(i);
            Optional<ApprovedTSAType> match = approvedTspTypes.stream().filter(
                    globalConfTsp -> globalConfTsp.getName().equals(serverConfTsp.getName())).findAny();
            if (match.isPresent() && !match.get().getUrl().equals(serverConfTsp.getUrl())) {
                log.debug("Detected changed TSP URL, Name: {}, Old URL: {}, New URL: {}",
                        serverConfTsp.getName(), serverConfTsp.getUrl(), match.get().getUrl());
                log.debug("Saving new TSP URL");
                serverConf.getTsp().get(i).setUrl(match.get().getUrl());
            } else {
                if (match.isPresent()) {
                    log.debug("isPresent: {}, Old URL: {}, New URL: {}", match.isPresent(),
                            serverConfTsp.getUrl(), match.get().getUrl());
                } else {
                    log.debug("isPresent: {}", match.isPresent());
                }
            }
        }
    }

    private SecurityServerId buildSecurityServerId(ClientId ownerId, String serverCode) {
        return SecurityServerId.create(
                ownerId.getXRoadInstance(), ownerId.getMemberClass(),
                ownerId.getMemberCode(), serverCode);
    }

    private SecurityServerId buildSecurityServerId(ServerConfType serverConf) {
        ClientId ownerId = serverConf.getOwner().getIdentifier();
        return buildSecurityServerId(ownerId, serverConf.getServerCode());
    }

    private void updateOwner(ServerConfType serverConf) throws Exception {
        ClientId ownerId = serverConf.getOwner().getIdentifier();
        for (ClientType client : serverConf.getClient()) {
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
                if (globalConfFacade.getServerOwner(altSecurityServerId) != null
                        && cert != null
                        && altSecurityServerId.equals(globalConfFacade.getServerId(cert))
                ) {
                    log.debug("Set \"{}\" as new owner", client.getIdentifier());
                    serverConf.setOwner(client);
                }
            }
        }
    }

    private X509Certificate getAuthCert(SecurityServerId serverId) throws Exception {
        log.debug("Get auth cert for security server '{}'", serverId);

        AuthKeyInfo keyInfo = signerProxyFacade.execute(new GetAuthKey(serverId));
        if (keyInfo != null && keyInfo.getCert() != null) {
            return readCertificate(keyInfo.getCert().getCertificateBytes());
        }
        log.warn("Failed to read authentication key");
        return null;
    }

    private void updateClientStatuses(ServerConfType serverConf,
            SecurityServerId securityServerId) throws Exception {
        log.debug("Updating client statuses");

        for (ClientType client : serverConf.getClient()) {
            boolean registered = globalConfFacade.isSecurityServerClient(
                    client.getIdentifier(), securityServerId);

            log.debug("Client '{}' registered = '{}'", client.getIdentifier(),
                    registered);

            if (registered && client.getClientStatus() != null) {
                switch (client.getClientStatus()) {
                    case ClientType.STATUS_REGISTERED:
                        // do nothing
                        break;
                    case ClientType.STATUS_SAVED: // FALL-THROUGH
                    case ClientType.STATUS_REGINPROG: // FALL-THROUGH
                    case ClientType.STATUS_GLOBALERR:
                        client.setClientStatus(ClientType.STATUS_REGISTERED);
                        log.debug("Setting client '{}' status to '{}'",
                                client.getIdentifier(),
                                client.getClientStatus());
                        break;
                    default:
                        log.warn("Unexpected status {} for client '{}'",
                                client.getIdentifier(),
                                client.getClientStatus());
                }
            }

            if (!registered && ClientType.STATUS_REGISTERED.equals(
                    client.getClientStatus())) {
                client.setClientStatus(ClientType.STATUS_GLOBALERR);

                log.debug("Setting client '{}' status to '{}'",
                        client.getIdentifier(), client.getClientStatus());
            }
        }
    }

    private void updateAuthCertStatuses(SecurityServerId securityServerId)
            throws Exception {
        log.debug("Updating auth cert statuses");

        signerProxyFacade.getTokens().stream().flatMap(t -> t.getKeyInfo().stream())
                .filter(k -> KeyUsageInfo.AUTHENTICATION.equals(k.getUsage()))
                .flatMap(k -> k.getCerts().stream()).forEach(certInfo -> {
                    try {
                        updateCertStatus(securityServerId, certInfo);
                    } catch (Exception e) {
                        throw translateException(e);
                    }
                });
    }

    private void updateCertStatus(SecurityServerId securityServerId,
            CertificateInfo certInfo) throws Exception {
        X509Certificate cert =
                readCertificate(certInfo.getCertificateBytes());

        boolean registered =
                securityServerId.equals(globalConfFacade.getServerId(cert));

        if (registered && certInfo.getStatus() != null) {
            switch (certInfo.getStatus()) {
                case CertificateInfo.STATUS_REGISTERED:
                    // do nothing
                    break;
                case CertificateInfo.STATUS_SAVED:
                case CertificateInfo.STATUS_REGINPROG:
                case CertificateInfo.STATUS_GLOBALERR:
                    log.debug("Setting certificate '{}' status to '{}'",
                            CertUtils.identify(cert),
                            CertificateInfo.STATUS_REGISTERED);

                    signerProxyFacade.setCertStatus(certInfo.getId(),
                            CertificateInfo.STATUS_REGISTERED);
                    break;
                default:
                    log.warn("Unexpected status '{}' for certificate '{}'",
                            certInfo.getStatus(), CertUtils.identify(cert));
            }

        }

        if (!registered && CertificateInfo.STATUS_REGISTERED.equals(
                certInfo.getStatus())) {
            log.debug("Setting certificate '{}' status to '{}'",
                    CertUtils.identify(cert),
                    CertificateInfo.STATUS_GLOBALERR);

            signerProxyFacade.setCertStatus(certInfo.getId(),
                    CertificateInfo.STATUS_GLOBALERR);
        }
    }
}

/**
 * The MIT License
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
package ee.ria.xroad.proxyui;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Job that checks whether globalconf has changed.
 */
@Slf4j
@DisallowConcurrentExecution
public class GlobalConfChecker implements Job {
    private boolean reloadServerConf = false;

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        try {
            reloadServerConf = false;
            checkGlobalConf();
            if (reloadServerConf) {
                // Reload server conf to update cache
                ServerConf.reload();
            }
        } catch (Exception e) {
            log.error("Checking globalconf for updates failed", e);
            throw new JobExecutionException(e);
        }
    }

    private void checkGlobalConf() throws Exception {
        GlobalConf.verifyValidity();

        log.debug("Reloading globalconf");
        GlobalConf.reload(); // XXX: temporary fix

        updateAuthCertStatuses(doInTransaction(session -> {
            ServerConfType serverConf = new ServerConfDAOImpl().getConf();
            SecurityServerId securityServerId = null;

            try {
                securityServerId = getSecurityServerId(serverConf);
                log.debug("Security Server ID is \"{}\"", securityServerId);
                updateClientStatuses(serverConf, securityServerId);
            } catch (Exception e) {
                throw translateException(e);
            }

            session.update(serverConf);

            return securityServerId;
        }));
    }

    private SecurityServerId buildSecurityServerId(ClientId ownerId, String serverCode) {
        return SecurityServerId.create(
                ownerId.getXRoadInstance(), ownerId.getMemberClass(),
                ownerId.getMemberCode(), serverCode);
    }

    private SecurityServerId getSecurityServerId(ServerConfType serverConf) throws Exception {
        ClientId ownerId = serverConf.getOwner().getIdentifier();
        SecurityServerId securityServerId = buildSecurityServerId(ownerId, serverConf.getServerCode());

        // Verify that the server id exists in global conf
        if (GlobalConf.getServerOwner(securityServerId) == null) {
            log.trace("Security Server ID \"{}\" not found in global conf", securityServerId);
            // If not, try to build an alternative server id
            SecurityServerId altSecurityServerId = findAltSecurityServerId(serverConf, ownerId);
            if (altSecurityServerId != null) {
                return altSecurityServerId;
            }
        }
        return securityServerId;

    }

    private SecurityServerId findAltSecurityServerId(ServerConfType serverConf, ClientId ownerId)
            throws Exception {
        for (ClientType client : serverConf.getClient()) {
            // Look for another member that is not the owner
            if (client.getIdentifier().getSubsystemCode() == null
                    && !client.getIdentifier().equals(ownerId)) {
                log.trace("Found another member: \"{}\"", client.getIdentifier());

                // Build a new server id using the alternative member as owner
                SecurityServerId altSecurityServerId = buildSecurityServerId(client.getIdentifier(),
                        serverConf.getServerCode());

                // Does the alternative server id exist in global conf?
                if (GlobalConf.getServerOwner(altSecurityServerId) != null) {
                    log.trace("Alternative Server ID \"{}\" exists in global conf", altSecurityServerId);
                    // Update server owner
                    serverConf.setOwner(client);
                    reloadServerConf = true;
                    return altSecurityServerId;
                }
            }
        }
        return null;
    }

    private void updateClientStatuses(ServerConfType serverConf,
            SecurityServerId securityServerId) throws Exception {
        log.debug("Updating client statuses");

        for (ClientType client : serverConf.getClient()) {
            boolean registered = GlobalConf.isSecurityServerClient(
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

        SignerProxy.getTokens().stream().flatMap(t -> t.getKeyInfo().stream())
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
                securityServerId.equals(GlobalConf.getServerId(cert));

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

                    SignerProxy.setCertStatus(certInfo.getId(),
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

            SignerProxy.setCertStatus(certInfo.getId(),
                    CertificateInfo.STATUS_GLOBALERR);
        }
    }
}

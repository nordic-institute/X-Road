package ee.ria.xroad.proxyui;

import java.security.cert.X509Certificate;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Job that checks whether globalconf has changed.
 */
@Slf4j
@DisallowConcurrentExecution
public class GlobalConfChecker implements Job {

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        try {
            checkGlobalConf();
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

            ClientId ownerId = serverConf.getOwner().getIdentifier();
            SecurityServerId securityServerId = SecurityServerId.create(
                    ownerId.getXRoadInstance(), ownerId.getMemberClass(),
                    ownerId.getMemberCode(), serverConf.getServerCode());

            try {
                updateClientStatuses(serverConf, securityServerId);
            } catch (Exception e) {
                throw translateException(e);
            }

            session.update(serverConf);

            return securityServerId;
        }));
    }

    private void updateClientStatuses(ServerConfType serverConf,
            SecurityServerId securityServerId) throws Exception {
        log.debug("updating client statuses");

        for (ClientType client : serverConf.getClient()) {
            boolean registered = GlobalConf.isSecurityServerClient(
                    client.getIdentifier(), securityServerId);

            log.debug("client '{}' registered = '{}'", client.getIdentifier(),
                    registered);

            if (registered && client.getClientStatus() != null) {
                switch (client.getClientStatus()) {
                    case ClientType.STATUS_SAVED: // FALL-THROUGH
                    case ClientType.STATUS_REGINPROG: // FALL-THROUGH
                    case ClientType.STATUS_GLOBALERR:
                        client.setClientStatus(ClientType.STATUS_REGISTERED);
                        log.debug("setting client '{}' status to '{}'",
                                client.getIdentifier(),
                                client.getClientStatus());
                        break;
                    default:
                        log.warn("unexpected client '{}' status: {}",
                                client.getIdentifier(),
                                client.getClientStatus());
                }
            }

            if (!registered && ClientType.STATUS_REGISTERED.equals(
                    client.getClientStatus())) {
                client.setClientStatus(ClientType.STATUS_GLOBALERR);

                log.debug("setting client '{}' status to '{}'",
                        client.getIdentifier(), client.getClientStatus());
            }
        }
    }

    private void updateAuthCertStatuses(SecurityServerId securityServerId)
            throws Exception {
        log.debug("updating auth cert statuses");

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
                case CertificateInfo.STATUS_SAVED:
                case CertificateInfo.STATUS_REGINPROG:
                case CertificateInfo.STATUS_GLOBALERR:
                    log.debug("setting cert '{}' status to '{}'",
                            certInfo.getId(),
                            CertificateInfo.STATUS_REGISTERED);

                    SignerProxy.setCertStatus(certInfo.getId(),
                            CertificateInfo.STATUS_REGISTERED);
                    break;
                default:
                    log.warn("unexpected cert '{}' status: {}",
                            certInfo.getId(),
                            certInfo.getStatus());
            }

        }

        if (!registered && CertificateInfo.STATUS_REGISTERED.equals(
                certInfo.getStatus())) {
            log.debug("setting cert '{}' status to '{}'", certInfo.getId(),
                    CertificateInfo.STATUS_GLOBALERR);

            SignerProxy.setCertStatus(certInfo.getId(),
                    CertificateInfo.STATUS_GLOBALERR);
        }
    }
}

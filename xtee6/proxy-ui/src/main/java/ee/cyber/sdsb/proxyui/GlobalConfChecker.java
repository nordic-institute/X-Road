package ee.cyber.sdsb.proxyui;

import java.security.cert.X509Certificate;

import lombok.extern.slf4j.Slf4j;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.model.ClientType;
import ee.cyber.sdsb.common.conf.serverconf.model.ServerConfType;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.commonui.SignerProxy;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;
import static ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;

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
            ServerConfType serverConf =
                    ServerConfDAOImpl.getInstance().getConf();

            ClientId ownerId = serverConf.getOwner().getIdentifier();
            SecurityServerId securityServerId = SecurityServerId.create(
                    ownerId.getSdsbInstance(), ownerId.getMemberClass(),
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

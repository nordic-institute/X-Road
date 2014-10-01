package ee.cyber.sdsb.proxyui;

import java.security.cert.X509Certificate;

import org.hibernate.Session;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.GlobalConfImpl;
import ee.cyber.sdsb.common.conf.GlobalConfProvider;
import ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.cyber.sdsb.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.model.ClientType;
import ee.cyber.sdsb.common.conf.serverconf.model.ServerConfType;
import ee.cyber.sdsb.common.db.TransactionCallback;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.commonui.SignerProxy;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

@DisallowConcurrentExecution
public class GlobalConfChecker implements Job {

    private static final Logger LOG =
        LoggerFactory.getLogger(GlobalConfChecker.class);

    private static GlobalConfProvider globalConf;

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        try {
            checkGlobalConf();
        } catch (Exception e) {
            LOG.error("Checking globalconf for updates failed", e);
            throw new JobExecutionException(e);
        }
    }

    private void checkGlobalConf() throws Exception {
        if (globalConf != null && !globalConf.hasChanged()) {
            LOG.debug("Globalconf has not been changed");
            return;
        }

        LOG.debug("Reloading globalconf");

        globalConf = new GlobalConfImpl(SystemProperties.getGlobalConfFile());

        // TODO: use ServerConf.getIdentifier
        SecurityServerId securityServerId =
                ServerConfDatabaseCtx.doInTransaction(
                        new TransactionCallback<SecurityServerId>() {
                @Override
                public SecurityServerId call(Session session) throws Exception {
                    ServerConfType serverConf =
                        ServerConfDAOImpl.getInstance().getConf();

                    ClientId ownerId = serverConf.getOwner().getIdentifier();
                    SecurityServerId securityServerId = SecurityServerId.create(
                        ownerId.getSdsbInstance(), ownerId.getMemberClass(),
                        ownerId.getMemberCode(), serverConf.getServerCode());

                    updateClientStatuses(serverConf, securityServerId);

                    session.update(serverConf);

                    return securityServerId;
                }
            });

        updateAuthCertStatuses(securityServerId);
    }

    private void updateClientStatuses(ServerConfType serverConf,
            SecurityServerId securityServerId) throws Exception {

        LOG.debug("updating client statuses");

        ClientId ownerId = serverConf.getOwner().getIdentifier();

        for (ClientType client : serverConf.getClient()) {
            boolean registered = globalConf.isSecurityServerClient(
                client.getIdentifier(), securityServerId);

            LOG.debug("client '{}' registered = '{}'",
                client.getIdentifier(), registered);

            if (registered &&
                (ClientType.STATUS_REGINPROG.equals(client.getClientStatus()) ||
                 ClientType.STATUS_GLOBALERR.equals(client.getClientStatus()))) {

                client.setClientStatus(ClientType.STATUS_REGISTERED);

                LOG.debug("setting client '{}' status to '{}'",
                    client.getIdentifier(), client.getClientStatus());
            }

            // owner skips ClientType.STATUS_REGINPROG
            if (registered && client.getIdentifier().equals(ownerId) &&
                    ClientType.STATUS_SAVED.equals(client.getClientStatus())) {
                client.setClientStatus(ClientType.STATUS_REGISTERED);

                LOG.debug("setting client '{}' status to '{}'",
                    client.getIdentifier(), client.getClientStatus());
            }

            if (!registered && ClientType.STATUS_REGISTERED.equals(
                    client.getClientStatus())) {
                client.setClientStatus(ClientType.STATUS_GLOBALERR);

                LOG.debug("setting client '{}' status to '{}'",
                    client.getIdentifier(), client.getClientStatus());
            }
        }
    }

    private void updateAuthCertStatuses(SecurityServerId securityServerId)
            throws Exception {

        LOG.debug("updating auth cert statuses");

        for (TokenInfo token : SignerProxy.getTokens()) {
            for (KeyInfo key : token.getKeyInfo()) {

                if (!KeyUsageInfo.AUTHENTICATION.equals(key.getUsage())) {
                    continue;
                }

                for (CertificateInfo certInfo : key.getCerts()) {
                    X509Certificate cert = CryptoUtils.readCertificate(
                        certInfo.getCertificateBytes());

                    boolean registered =
                        globalConf.hasAuthCert(cert, securityServerId);

                    if (registered &&
                        (CertificateInfo.STATUS_SAVED
                             .equals(certInfo.getStatus()) ||
                         CertificateInfo.STATUS_REGINPROG
                             .equals(certInfo.getStatus()) ||
                         CertificateInfo.STATUS_GLOBALERR
                             .equals(certInfo.getStatus()))) {

                        LOG.debug("setting cert '{}' status to '{}'",
                            certInfo.getId(), CertificateInfo.STATUS_REGISTERED);

                        SignerProxy.setCertStatus(
                            certInfo.getId(), CertificateInfo.STATUS_REGISTERED);
                    }

                    if (!registered && CertificateInfo.STATUS_REGISTERED.equals(
                            certInfo.getStatus())) {

                        LOG.debug("setting cert '{}' status to '{}'",
                            certInfo.getId(), CertificateInfo.STATUS_GLOBALERR);

                        SignerProxy.setCertStatus(
                            certInfo.getId(), CertificateInfo.STATUS_GLOBALERR);
                    }
                }
            }
        }
    }
}

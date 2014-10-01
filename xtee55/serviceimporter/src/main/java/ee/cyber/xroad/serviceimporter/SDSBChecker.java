package ee.cyber.xroad.serviceimporter;

import java.util.List;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ee.cyber.sdsb.common.conf.serverconf.model.ClientType;
import ee.cyber.sdsb.common.db.TransactionCallback;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.ListTokens;

import static ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;


public class SDSBChecker {

    private static final Logger LOG =
            LoggerFactory.getLogger(SDSBChecker.class);

    private ActorSystem actorSystem;

    public boolean canActivate() throws Exception {
        return doInTransaction(new TransactionCallback<Boolean>() {
            @Override
            public Boolean call(Session session) throws Exception {
                return internalCanActivate();
            }
        });
    }

    public boolean internalCanActivate() throws Exception {
        if (!Helper.confExists()) {
            return false;
        }

        ClientType owner = Helper.getConf().getOwner();
        ClientId ownerId = owner.getIdentifier();

        boolean authCertExists = false;
        boolean ownerSignCertExists = false;
        boolean tspExists = !Helper.getConf().getTsp().isEmpty();

        tokens:
        for (TokenInfo token : getTokens()) {
            for (KeyInfo key : token.getKeyInfo()) {
                for (CertificateInfo cert : key.getCerts()) {
                    if (KeyUsageInfo.AUTHENTICATION.equals(key.getUsage())
                            && "registered".equals(cert.getStatus())) {
                        authCertExists = true;
                    }

                    if (KeyUsageInfo.SIGNING.equals(key.getUsage())
                            && ownerId.equals(cert.getMemberId())) {
                        ownerSignCertExists = true;
                    }

                    if (authCertExists && ownerSignCertExists) {
                        break tokens;
                    }
                }
            }
        }

        if (!authCertExists) {
            logAndPrint("The security server does not have a registered authentication certificate. Please create an authentication certificate and register it at the X-Road central server.");
        }

        if (!ownerSignCertExists) {
            logAndPrint("Owner of the security server does not have signing certificate. Please create signing certificate for the owner.");
        }

        if (!tspExists) {
            logAndPrint("There are no configured time-stamping services. Please configure at least one time-stamping service.");
        }

        return authCertExists && ownerSignCertExists && tspExists;
    }

    private List<TokenInfo> getTokens() throws Exception {
        List<TokenInfo> tokens = null;
        try {
            startSignerClient();
            tokens = SignerClient.execute(new ListTokens());
        } finally {
            stopSignerClient();
        }

        LOG.debug("Received tokens: {}", tokens.toString());
        return tokens;
    }

    private void startSignerClient() throws Exception {
        if (actorSystem == null) {
            LOG.debug("Creating ActorSystem...");

            Config config = ConfigFactory.load().getConfig("sdsbchecker");

            LOG.debug("Akka using configuration: {}", config);
            actorSystem = ActorSystem.create("SDSBChecker", config);
        }

        SignerClient.init(actorSystem);
    }

    private void stopSignerClient() throws Exception {
        LOG.debug("stopSignerClient()");

        if (actorSystem != null) {
            actorSystem.shutdown();
        }
    }

    private static void logAndPrint(String message) {
        System.out.println(message);
        LOG.info(message);
    }
}

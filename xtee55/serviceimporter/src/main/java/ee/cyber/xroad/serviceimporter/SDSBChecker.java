package ee.cyber.xroad.serviceimporter;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.conf.serverconf.model.ClientType;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.ListTokens;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;
import static ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;


class SDSBChecker {

    private static final Logger LOG =
            LoggerFactory.getLogger(SDSBChecker.class);

    private ActorSystem actorSystem;

    boolean canActivate() throws Exception {
        return doInTransaction(session -> {
            try {
                return internalCanActivate();
            } catch (Exception e) {
                throw translateException(e);
            }
        });
    }

    private boolean internalCanActivate() throws Exception {
        if (!Helper.confExists()) {
            return false;
        }

        if (!checkValidGlobalConf()) {
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

    boolean canPromote() {
        return checkValidGlobalConf();
    }

    private boolean checkValidGlobalConf() {
        if (!GlobalConf.isValid()) {
            logAndPrint("The security server does not have up-to-date global configuration. Please fix the configuration downloading before proceeding.");
            return false;
        }
        return true;
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

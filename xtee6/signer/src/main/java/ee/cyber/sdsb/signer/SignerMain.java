package ee.cyber.sdsb.signer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.AdminPort;
import ee.cyber.sdsb.signer.core.device.DeviceTypeConf;

import static ee.cyber.sdsb.signer.protocol.ComponentNames.SIGNER;

public class SignerMain {

    private static final Logger LOG =
            LoggerFactory.getLogger(SignerMain.class);

    private static ActorSystem actorSystem;

    public static void main(String[] args) throws Exception {
        try {
            startUp();
        } catch (Throwable fatal) {
            LOG.error("FATAL", fatal);
            System.exit(1);
        }
    }

    private static void startUp() throws Exception {
        int signerPort = SystemProperties.getSignerPort();

        LOG.info("Starting Signer on port {}...", signerPort);

        AdminPort adminPort = createAdminPort(signerPort + 1);

        DeviceTypeConf.reload();

        actorSystem = ActorSystem.create(SIGNER, getConf(signerPort));
        try {
            adminPort.start();

            Signer signer = new Signer(actorSystem);
            signer.start();

            actorSystem.awaitTermination();

            signer.stop();
            signer.join();

            adminPort.stop();
            adminPort.join();
        } finally {
            actorSystem.shutdown();
        }
    }

    private static AdminPort createAdminPort(int signerPort) {
        AdminPort adminPort = new AdminPort(signerPort);
        adminPort.addStopHandler(new AdminPort.AsynchronousCallback() {
            @Override
            public void run() {
                LOG.info("Shutting down...");

                actorSystem.shutdown();
            }
        });

        return adminPort;
    }

    private static Config getConf(int signerPort) {
        Config conf = ConfigFactory.load().getConfig("signer-main");

        return conf.withValue("akka.remote.netty.tcp.port",
                ConfigValueFactory.fromAnyRef(signerPort));
    }
}

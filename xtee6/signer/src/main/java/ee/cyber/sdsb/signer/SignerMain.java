package ee.cyber.sdsb.signer;

import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorSystem;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.AdminPort;

import static ee.cyber.sdsb.signer.protocol.ComponentNames.SIGNER;

@Slf4j
public class SignerMain {

    private static ActorSystem actorSystem;
    private static Signer signer;
    private static AdminPort adminPort;

    public static void main(String[] args) throws Exception {
        try {
            startup();
        } catch (Throwable fatal) {
            log.error("FATAL", fatal);
            System.exit(1);
        }
    }

    private static void startup() throws Exception {
        int signerPort = SystemProperties.getSignerPort();

        log.info("Starting Signer on port {}...", signerPort);

        adminPort = createAdminPort(signerPort + 1);

        actorSystem = ActorSystem.create(SIGNER, getConf(signerPort));
        adminPort.start();

        signer = new Signer(actorSystem);
        signer.start();

        actorSystem.awaitTermination();

        shutdown();
    }

    private static void shutdown() {
        log.info("Signer shutting down...");

        try {
            signer.stop();
            signer.join();
        } catch (Exception ignore) {
        }

        try {
            adminPort.stop();
            adminPort.join();
        } catch (Exception ignore) {
        }

        actorSystem.shutdown();
    }

    private static AdminPort createAdminPort(int signerPort) {
        AdminPort adminPort = new AdminPort(signerPort);
        adminPort.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                shutdown();
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

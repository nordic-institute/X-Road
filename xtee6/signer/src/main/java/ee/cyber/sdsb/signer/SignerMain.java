package ee.cyber.sdsb.signer;

import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorSystem;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.SystemPropertiesLoader;
import ee.cyber.sdsb.common.util.AdminPort;

import static ee.cyber.sdsb.common.SystemProperties.CONF_FILE_PROXY;
import static ee.cyber.sdsb.common.SystemProperties.CONF_FILE_SIGNER;
import static ee.cyber.sdsb.signer.protocol.ComponentNames.SIGNER;

/**
 * Signer main program.
 */
@Slf4j
public final class SignerMain {

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_PROXY)
            .with(CONF_FILE_SIGNER)
            .load();
    }

    private static ActorSystem actorSystem;
    private static Signer signer;
    private static AdminPort adminPort;

    private SignerMain() {
    }

    /**
     * Entry point to Signer.
     * @param args the arguments
     * @throws Exception if an error occurs
     */
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
        } catch (Exception e) {
            log.error("Error stopping signer", e);
        }

        try {
            adminPort.stop();
            adminPort.join();
        } catch (Exception e) {
            log.error("Error stopping admin port", e);
        }

        actorSystem.shutdown();
    }

    private static AdminPort createAdminPort(int signerPort) {
        AdminPort port = new AdminPort(signerPort);
        port.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                shutdown();
            }
        });

        return port;
    }

    private static Config getConf(int signerPort) {
        Config conf = ConfigFactory.load().getConfig("signer-main");
        return conf.withValue("akka.remote.netty.tcp.port",
                ConfigValueFactory.fromAnyRef(signerPort));
    }
}

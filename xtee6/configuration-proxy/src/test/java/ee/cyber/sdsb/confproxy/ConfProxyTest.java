package ee.cyber.sdsb.confproxy;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import lombok.extern.slf4j.Slf4j;
import ee.cyber.sdsb.common.conf.globalconf.ConfigurationDirectory;
import ee.cyber.sdsb.signer.protocol.SignerClient;

/**
 * Test program for the configuration proxy, uses a pre-downloaded configuration.
 */
@Slf4j
public final class ConfProxyTest {

    private static ActorSystem actorSystem;

    private static ConfProxy proxy;

    private ConfProxyTest() {
        
    }

    /**
     * Configuration proxy test program entry point.
     * @param args program args
     * @throws Exception in case configuration proxy fails to start
     */
    public static void main(String[] args) throws Exception {
        try {
            setup();
            proxy.execute();
        } catch (Throwable t) {
            log.error("Error when executing configuration-proxy", t);
        } finally {
            shutdown();
        }
    }

    private static void setup() throws Exception {
        log.trace("startup()");

        actorSystem = ActorSystem.create("ConfigurationProxy",
                ConfigFactory.load().getConfig("configuration-proxy"));

        SignerClient.init(actorSystem);

        proxy = new ConfProxy("PROXY1") {
            @Override
            protected ConfigurationDirectory download() throws Exception {
                return new ConfigurationDirectory(
                        conf.getConfigurationDownloadPath());
            }
        };

        log.info("Starting configuration-proxy...");
    }

    private static void shutdown() throws Exception {
        log.trace("shutdown()");

        actorSystem.shutdown();
    }
}

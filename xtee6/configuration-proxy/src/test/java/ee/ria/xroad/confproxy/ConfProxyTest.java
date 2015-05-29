package ee.ria.xroad.confproxy;

import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.conf.globalconf.ConfigurationDirectory;
import ee.ria.xroad.confproxy.util.ConfProxyHelper;
import ee.ria.xroad.confproxy.util.OutputBuilder;
import ee.ria.xroad.signer.protocol.SignerClient;

/**
 * Test program for the configuration proxy,
 * uses a pre-downloaded configuration.
 */
@Slf4j
public final class ConfProxyTest {

    private static ActorSystem actorSystem;

    /**
     * Unavailable utility class constructor.
     */
    private ConfProxyTest() { }

    /**
     * Configuration proxy test program entry point.
     * @param args program args
     * @throws Exception in case configuration proxy fails to start
     */
    public static void main(final String[] args) throws Exception {
        try {
            actorSystem = ActorSystem.create("ConfigurationProxy",
                    ConfigFactory.load().getConfig("configuration-proxy"));
            SignerClient.init(actorSystem);

            ConfProxyProperties conf = new ConfProxyProperties("PROXY1");
            ConfProxyHelper.purgeOutdatedGenerations(conf);
            ConfigurationDirectory confDir = new ConfigurationDirectory(
                    conf.getConfigurationDownloadPath());

            OutputBuilder output = new OutputBuilder(confDir, conf);
            output.buildSignedDirectory();
            output.moveAndCleanup();
        } catch (Throwable t) {
            log.error("Error when executing configuration-proxy", t);
        } finally {
            actorSystem.shutdown();
        }
    }
}

package ee.ria.xroad.common;

import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import ee.ria.xroad.signer.protocol.SignerClient;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_CENTER;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_SIGNER;

/**
 * Contains the center actor system.
 */
public final class CenterServices {

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_CENTER)
            .with(CONF_FILE_SIGNER)
            .load();
    }

    private static ActorSystem actorSystem;

    private CenterServices() {
    }

    private static void init() throws Exception {
        if (actorSystem == null) {
            actorSystem = ActorSystem.create("CenterService",
                    ConfigFactory.load().getConfig("centerservice").withValue(
                            "akka.remote.quarantine-systems-for",
                            ConfigValueFactory.fromAnyRef("off")));
        }
    }

    /**
     * Initializes the center actor system.
     * @throws Exception in case of any errors
     */
    public static void start() throws Exception {
        init();

        SignerClient.init(actorSystem);
    }

    /**
     * Stops the center actor system.
     * @throws Exception in case of any errors
     */
    public static void stop() throws Exception {
        if (actorSystem != null) {
            actorSystem.shutdown();
        }
    }

}

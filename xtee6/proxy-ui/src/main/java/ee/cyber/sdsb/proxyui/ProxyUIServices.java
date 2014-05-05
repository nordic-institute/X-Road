package ee.cyber.sdsb.proxyui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ee.cyber.sdsb.signer.protocol.SignerClient;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;

public class ProxyUIServices {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProxyUIServices.class);

    private static ActorSystem actorSystem;

    private static void init() throws Exception {
        if (actorSystem == null) {
            LOG.debug("Creating ActorSystem...");

            // TODO: this hardcoded configuration should ideally be loaded from application.conf file
            Config config = config(new String[][] {
                    {"akka.remote.quarantine-systems-for", "off"},
                    {"akka.remote.gate-invalid-addresses-for", "2s"},
            });

            LOG.debug("Akka using configuration: {}", config);
            actorSystem = ActorSystem.create("ProxyUI", config);
        }
    }

    public static void start() throws Exception {
        LOG.info("start()");

        init();

        SignerClient.init(actorSystem);
    }

    public static void stop() throws Exception {
        LOG.info("stop()");

        if (actorSystem != null) {
            actorSystem.shutdown();
        }
    }

    private static Config config(String[][] config) {
        Config result = ConfigFactory.load().getConfig("proxyui");
        for (String[] keyValue : config) {
            result = result.withValue(keyValue[0], fromAnyRef(keyValue[1]));
        }

        return result;
    }

}

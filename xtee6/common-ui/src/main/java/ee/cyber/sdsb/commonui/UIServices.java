package ee.cyber.sdsb.commonui;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;

public class UIServices {
    private static final Logger LOG = LoggerFactory.getLogger(UIServices.class);

    private String configName;
    private ActorSystem actorSystem;

    public UIServices(String actorSystemName, String configName) {
        this.configName = configName;

        LOG.debug("Creating ActorSystem...");

        // TODO: this hardcoded configuration should ideally be loaded from
        // application.conf file
        Config config = config(new String[][] {
                { "akka.remote.quarantine-systems-for", "off" },
                { "akka.remote.gate-invalid-addresses-for", "2s" },
        });

        LOG.debug("Akka using configuration: {}", config);
        actorSystem = ActorSystem.create(actorSystemName, config);
    }

    public ActorSystem getActorSystem() {
        return actorSystem;
    }

    public void stop() throws Exception {
        LOG.info("stop()");

        if (actorSystem != null) {
            actorSystem.shutdown();
        }
    }

    private Config config(String[][] config) {
        Config result = ConfigFactory.load().getConfig(configName);
        for (String[] keyValue : config) {
            result = result.withValue(keyValue[0], fromAnyRef(keyValue[1]));
        }

        return result;
    }
}

package ee.cyber.sdsb.confproxy;

import java.util.Arrays;
import java.util.List;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import lombok.extern.slf4j.Slf4j;
import ee.cyber.sdsb.common.SystemPropertiesLoader;
import ee.cyber.sdsb.confproxy.util.ConfProxyHelper;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import static ee.cyber.sdsb.common.SystemProperties.CONF_FILE_CONFPROXY;

/**
 * Main program for the configuration proxy.
 */
@Slf4j
public final class ConfProxyMain {

    static {
        new SystemPropertiesLoader() {
            @Override
            protected void loadWithCommonAndLocal() {
                load(CONF_FILE_CONFPROXY, "configuration-proxy");
            }
        };
    }

    private static ActorSystem actorSystem;

    private ConfProxyMain() {
        
    }

    /**
     * Configuration proxy program entry point.
     * @param args program args
     * @throws Exception in case configuration proxy fails to start
     */
    public static void main(String[] args) throws Exception {
        try {
            setup();
            execute(args);
        } catch (Throwable t) {
            log.error("Configuration proxy failed to start", t);
            throw t;
        } finally {
            shutdown();
        }
    }

    private static void setup() throws Exception {
        log.trace("startup()");

        actorSystem = ActorSystem.create("ConfigurationProxy",
                ConfigFactory.load().getConfig("configuration-proxy"));

        SignerClient.init(actorSystem);
    }

    private static void execute(String[] args) throws Exception {
        List<String> instances;

        if (args.length > 0) {
            instances = Arrays.asList(args);
        } else {
            instances = ConfProxyHelper.availableInstances();
        }

        for (String instance: instances) {
            try {
                ConfProxy proxy = new ConfProxy(instance);
                proxy.execute();
            } catch (Exception ex) {
                log.error("Error when executing configuration-proxy '{}': {}",
                        instance, ex);
            }
        }
    }

    private static void shutdown() throws Exception {
        log.trace("shutdown()");

        actorSystem.shutdown();
    }
}

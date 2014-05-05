package ee.cyber.sdsb.common;

import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import ee.cyber.sdsb.signer.protocol.SignerClient;

public class CenterServices {

    private static ActorSystem actorSystem;

    private static void init() throws Exception {
        if (actorSystem == null) {
            actorSystem = ActorSystem.create("CenterService",
                    ConfigFactory.load().getConfig("centerservice").withValue(
                            "akka.remote.quarantine-systems-for",
                            ConfigValueFactory.fromAnyRef("off")));
        }
    }

    public static void start() throws Exception {
        init();

        SignerClient.init(actorSystem);
    }

    public static void stop() throws Exception {
        if (actorSystem != null) {
            actorSystem.shutdown();
        }
    }

}

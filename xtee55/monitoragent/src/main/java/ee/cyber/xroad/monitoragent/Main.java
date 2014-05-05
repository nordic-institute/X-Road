package ee.cyber.xroad.monitoragent;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        LOG.info("Monitor agent starting up.");

        ActorSystem actorSystem = ActorSystem.create("XroadMonitoringAgent",
                ConfigFactory.load().getConfig("monitoringagent"));

        actorSystem.actorOf(Props.create(DataReceiver.class), "DataReceiver");
        actorSystem.awaitTermination();

        actorSystem.shutdown();
        LOG.info("Monitor agent starting shutting down.");
    }
}

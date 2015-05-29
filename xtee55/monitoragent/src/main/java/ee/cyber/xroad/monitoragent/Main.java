package ee.cyber.xroad.monitoragent;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Main monitoring agent program.
 */
@Slf4j
public final class Main {

    private Main() {
    }

    /**
     * Main program access point.
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        log.info("Monitor agent starting up.");

        ActorSystem actorSystem = ActorSystem.create("XroadMonitoringAgent",
                ConfigFactory.load().getConfig("monitoringagent"));

        actorSystem.actorOf(Props.create(DataReceiver.class), "DataReceiver");
        actorSystem.awaitTermination();

        actorSystem.shutdown();
        log.info("Monitor agent starting shutting down.");
    }
}

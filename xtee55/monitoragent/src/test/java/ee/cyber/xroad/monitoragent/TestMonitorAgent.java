package ee.cyber.xroad.monitoragent;

import java.util.Date;

import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MonitorAgent;

/**
 * Monitor agent test program.
 */
@Slf4j
public final class TestMonitorAgent {

    private TestMonitorAgent() {
    }

    /**
     * Main program access point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        log.debug("Starting up.");

        ActorSystem actorSystem = ActorSystem.create("TestMonitorAgent",
                ConfigFactory.load().getConfig("testmonitoragent"));

        MonitorAgent.init(actorSystem);

        while (true) {
            log.debug("Sending message");
            MonitorAgent.success(
                    new MessageInfo(
                        MessageInfo.Origin.SERVER_PROXY,
                        ClientId.create("EE", "ORG", "Foo"),
                        ServiceId.create("EE", "ORG", "bar", null, "service"),
                        "3232312313",
                        "1234567890"),
                    new Date(), new Date());
            Thread.sleep(1000);
            log.debug("Sending serverProxyFailed");
            MonitorAgent.serverProxyFailed(new MessageInfo(
                    MessageInfo.Origin.SERVER_PROXY,
                    ClientId.create("EE", "ORG", "Foo"),
                    ServiceId.create("EE", "ORG", "bar", null, "service"),
                    "3232312313",
                    "1234567890"));
            Thread.sleep(1000);
            log.debug("Sending fault");
            MonitorAgent.failure(null, "Foo", "Bar");
            Thread.sleep(1000);
            log.debug("Sending fault with message");
            MonitorAgent.failure(
                    new MessageInfo(
                        MessageInfo.Origin.SERVER_PROXY,
                        ClientId.create("EE", "ORG", "Foo"),
                        ServiceId.create("EE", "ORG", "bar", null, "service"),
                        "3232312313",
                        "1234567890"),
                    "Foo", "Bar");

            Thread.sleep(1000);
        }

//        actorSystem.shutdown();
//        LOG.debug("Shutting down.");
    }
}

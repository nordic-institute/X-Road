package ee.cyber.xroad.monitoragent;

import java.util.Date;

import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.MonitorAgent;

public class TestMonitorAgent {
    private static final Logger LOG = LoggerFactory.getLogger(
            TestMonitorAgent.class);

    public static void main(String[] args) throws Exception {
        LOG.debug("Starting up.");

        ActorSystem actorSystem = ActorSystem.create("TestMonitorAgent",
                ConfigFactory.load().getConfig("testmonitoragent"));

        MonitorAgent.init(actorSystem);

        while (true) {
            LOG.debug("Sending message");
            MonitorAgent.success(
                    new MessageInfo(
                        MessageInfo.Origin.SERVER_PROXY,
                        ClientId.create("EE", "ORG", "Foo"),
                        ServiceId.create("EE", "ORG", "bar", null, "service"),
                        "3232312313",
                        "1234567890"),
                    new Date(), new Date());
            Thread.sleep(1000);
            LOG.debug("Sending serverProxyFailed");
            MonitorAgent.serverProxyFailed(new MessageInfo(
                    MessageInfo.Origin.SERVER_PROXY,
                    ClientId.create("EE", "ORG", "Foo"),
                    ServiceId.create("EE", "ORG", "bar", null, "service"),
                    "3232312313",
                    "1234567890"));
            Thread.sleep(1000);
            LOG.debug("Sending fault");
            MonitorAgent.failure(null, "Foo", "Bar");
            Thread.sleep(1000);
            LOG.debug("Sending fault with message");
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

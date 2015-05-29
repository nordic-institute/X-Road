package ee.ria.xroad.common.monitoring;

import java.util.Date;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;

import ee.ria.xroad.common.SystemProperties;

/**
 * Default implementation of the monitor agent interface.
 */
public class DefaultMonitorAgentImpl implements MonitorAgentProvider {

    private final ActorSelection actor;

    /**
     * Constructs a monitor agent that uses the given actor system.
     * @param actorSystem actor system to be used by this monitor agent
     */
    public DefaultMonitorAgentImpl(ActorSystem actorSystem) {
        String actorName = getActorName();
        actor = actorName != null
                ? actorSystem.actorSelection(actorName)
                : null;
    }

    @Override
    public void success(MessageInfo messageInfo, Date startTime, Date endTime) {
        tell(new SuccessfulMessage(messageInfo, startTime, endTime));
    }

    @Override
    public void serverProxyFailed(MessageInfo messageInfo) {
        tell(new ServerProxyFailed(messageInfo));
    }

    @Override
    public void failure(MessageInfo messageInfo, String faultCode,
            String faultMessage) {
        tell(new FaultInfo(messageInfo, faultCode, faultMessage));
    }

    private void tell(Object message) {
        if (actor != null) {
            actor.tell(message, ActorRef.noSender());
        }
    }

    private static String getActorName() {
        return System.getProperty(SystemProperties.MONITORING_AGENT_URI);
    }
}

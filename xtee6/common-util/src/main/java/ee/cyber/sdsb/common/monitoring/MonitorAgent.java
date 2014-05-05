package ee.cyber.sdsb.common.monitoring;

import java.util.Date;

import akka.actor.ActorSystem;

/**
 * This class encapsulates monitoring agent that can receive
 * monitoring information.
 */
public class MonitorAgent {

    private static MonitorAgentProvider monitorAgentImpl;

    private MonitorAgent() {
    }

    /**
     * Initialize the MonitorAgent with given ActorSystem.
     * This method must be called before any other methods in this class.
     */
    public static void init(ActorSystem actorSystem) {
        monitorAgentImpl = new DefaultMonitorAgentImpl(actorSystem);
    }

    /**
     * Initialize the MonitorAgent with given implementation.
     * This method must be called before any other methods in this class.
     */
    public static void init(MonitorAgentProvider monitorAgentImpl) {
        MonitorAgent.monitorAgentImpl = monitorAgentImpl;
    }

    /**
     * Message was processed successfully by the proxy.
     * @param messageInfo Successfully processed message.
     * @param startTime Time of start of the processing.
     * @param endTime Time of end of the processing.
     */
    public static void success(MessageInfo messageInfo, Date startTime,
            Date endTime) {
        if (monitorAgentImpl != null) {
            monitorAgentImpl.success(messageInfo, startTime, endTime);
        }
    }

    /**
     * Client proxy failed to make connection to server proxy.
     */
    public static void serverProxyFailed(MessageInfo messageInfo) {
        if (monitorAgentImpl != null) {
            monitorAgentImpl.serverProxyFailed(messageInfo);
        }
    }

    /**
     * Processing of a given message failed for various reasons.
     * Parameter messageInfo can be null if the message is not available
     * at the point of the failure.
     */
    public static void failure(MessageInfo messageInfo,
            String faultCode, String faultMessage) {
        if (monitorAgentImpl != null) {
            monitorAgentImpl.failure(messageInfo, faultCode, faultMessage);
        }
    }

}

package ee.ria.xroad.common.monitoring;

import java.util.Date;

/**
 * Interface describing monitor agent functionality.
 */
public interface MonitorAgentProvider {

    /**
     * Message was processed successfully by the proxy.
     * @param messageInfo Successfully processed message.
     * @param startTime Time of start of the processing.
     * @param endTime Time of end of the processing.
     */
    void success(MessageInfo messageInfo, Date startTime, Date endTime);

    /**
     * Client proxy failed to make connection to server proxy.
     * @param messageInfo information about the message that could not be sent
     */
    void serverProxyFailed(MessageInfo messageInfo);

    /**
     * Processing of a given message failed for various reasons.
     * Parameter messageInfo can be null if the message is not available
     * at the point of the failure.
     * @param messageInfo information about the message that could not be processed
     * @param faultCode fault code of the failure
     * @param faultMessage fault message of the failure
     */
    void failure(MessageInfo messageInfo, String faultCode,
            String faultMessage);
}

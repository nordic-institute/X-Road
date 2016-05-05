/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.monitoring;

import java.util.Date;

import akka.actor.ActorSystem;

/**
 * This class encapsulates monitoring agent that can receive
 * monitoring information.
 */
public final class MonitorAgent {

    private static MonitorAgentProvider monitorAgentImpl;

    private MonitorAgent() {
    }

    /**
     * Initialize the MonitorAgent with given ActorSystem.
     * This method must be called before any other methods in this class.
     * @param actorSystem actor system to be used by this monitoring agent
     */
    public static void init(ActorSystem actorSystem) {
        monitorAgentImpl = new DefaultMonitorAgentImpl(actorSystem);
    }

    /**
     * Initialize the MonitorAgent with given implementation.
     * This method must be called before any other methods in this class.
     * @param implementation monitor agent implementation to be used by this monitoring agent
     */
    public static void init(MonitorAgentProvider implementation) {
        MonitorAgent.monitorAgentImpl = implementation;
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
     * @param messageInfo information about the message that could not be sent
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
     * @param messageInfo information about the message that could not be processed
     * @param faultCode fault code of the failure
     * @param faultMessage fault message of the failure
     */
    public static void failure(MessageInfo messageInfo,
            String faultCode, String faultMessage) {
        if (monitorAgentImpl != null) {
            monitorAgentImpl.failure(messageInfo, faultCode, faultMessage);
        }
    }

}

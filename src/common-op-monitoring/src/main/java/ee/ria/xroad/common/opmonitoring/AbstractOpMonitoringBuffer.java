/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.opmonitoring;

import akka.actor.UntypedAbstractActor;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract operational monitoring buffer.
 */
@Slf4j
public abstract class AbstractOpMonitoringBuffer extends UntypedAbstractActor {

    public static final String SEND_MONITORING_DATA = "sendMonitoringData";
    public static final String SENDING_SUCCESS = "sendingSuccess";
    public static final String SENDING_FAILURE = "sendingFailure";

    private static final String LOGGING_FORMAT = "onReceive: {}";

    @Override
    public void onReceive(Object message) throws Exception {
        try {
            if (message instanceof OpMonitoringData) {
                OpMonitoringData data = (OpMonitoringData) message;

                log.trace(LOGGING_FORMAT, data);

                store(data);
            } else if (message.equals(SEND_MONITORING_DATA)) {
                log.trace(LOGGING_FORMAT, SEND_MONITORING_DATA);

                send();
            } else if (message.equals(SENDING_SUCCESS)) {
                log.trace(LOGGING_FORMAT, SENDING_SUCCESS);

                sendingSuccess();
            } else if (message.equals(SENDING_FAILURE)) {
                log.trace(LOGGING_FORMAT, SENDING_FAILURE);

                sendingFailure();
            } else {
                unhandled(message);
            }
        } catch (Exception e) {
            log.error("Operational monitoring buffer failed", e);
        }
    }

    protected abstract void store(OpMonitoringData data) throws Exception;

    protected abstract void send() throws Exception;

    protected abstract void sendingSuccess() throws Exception;

    protected abstract void sendingFailure() throws Exception;

}

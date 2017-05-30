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
package ee.ria.xroad.signer;

import ee.ria.xroad.signer.certmanager.OcspClientWorker;
import ee.ria.xroad.signer.util.VariableIntervalPeriodicJob;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.signer.protocol.ComponentNames.OCSP_CLIENT;

/**
 * Periodically executes the OcspClient
 */
@Slf4j
public class OcspClientJob extends VariableIntervalPeriodicJob {

    public static final String CANCEL = "Cancel";
    public static final String FAILED = "Failed";
    public static final String SUCCESS = "Success";
    public static final String RESCHEDULE = "Reschedule";

    private static final FiniteDuration INITIAL_DELAY =
            FiniteDuration.create(5, TimeUnit.SECONDS);

    private static final int BASIC_DELAY = 10;
    private int nextDelay = 0;
    private int currentDelay = 0;
    private int prevDelay = 0;
    private boolean failed = false;

    OcspClientJob() {
        super(OCSP_CLIENT, OcspClientWorker.EXECUTE);
    }

    @Override
    protected FiniteDuration getInitialDelay() {
        return INITIAL_DELAY;
    }

    @Override
    protected FiniteDuration getNextDelay() {
        // Init first round
        if (currentDelay == 0) {
            prevDelay = 0;
            currentDelay = BASIC_DELAY;
        }
        // Use fibonacci number serie for counting increasing delay
        nextDelay = currentDelay + prevDelay;

        if (failed && nextDelay < OcspClientWorker.getNextOcspFetchIntervalSeconds()) {
            prevDelay = currentDelay;
            currentDelay = nextDelay;
            log.debug("Delay for next OCSP refresh: {}", nextDelay);
            return FiniteDuration.create(nextDelay, TimeUnit.SECONDS);
        } else {
            log.debug("Delay for greatest OSCP refresh time: {}", OcspClientWorker.getNextOcspFetchIntervalSeconds());
            return FiniteDuration.create(
                OcspClientWorker.getNextOcspFetchIntervalSeconds(),
                TimeUnit.SECONDS);
        }
    }

    @Override
    public void onReceive(Object incoming) throws Exception {
        if (CANCEL.equals(incoming)) {
            log.debug("received message OcspClientWorker.CANCEL");
            cancelNextSend();
        } else if (RESCHEDULE.equals(incoming)) {
            log.debug("received message OcspClientWorker.RESCHEDULE");
            scheduleNextSend(getNextDelay());
        } else if (SUCCESS.equals(incoming)) {
            log.debug("received message OcspClientJob.SUCCESS");
            failed = false;
            currentDelay = 0;
        } else if (FAILED.equals(incoming) && !failed) {
            log.debug("received message OcspClientJob.FAILED");
            cancelNextSend();
            failed = true;
            scheduleNextSend(getNextDelay());
        } else {
            log.debug("received unknown message {}", incoming);
            super.onReceive(incoming);
        }
    }
}

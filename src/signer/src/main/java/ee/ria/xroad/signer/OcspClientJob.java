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
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.FiniteDuration;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.signer.certmanager.OcspClientWorker.GLOBAL_CONF_INVALIDATED;
import static ee.ria.xroad.signer.protocol.ComponentNames.OCSP_CLIENT;

/**
 * Periodically executes OCSP-response refresh by sending {@link ee.ria.xroad.signer.certmanager.OcspClientWorker}
 * the message {@value OcspClientWorker#EXECUTE} and manages the refresh interval
 * based on the status of the last refresh.
 */
@Slf4j
public class OcspClientJob extends OcspRetrievalJob {

    public static final String CANCEL = "Cancel";
    public static final String FAILED = "Failed";
    public static final String SUCCESS = "Success";
    public static final String RESCHEDULE = "Reschedule";

    private static final FiniteDuration INITIAL_DELAY =
            FiniteDuration.create(5, TimeUnit.SECONDS);

    private static final int BASIC_DELAY = 10;
    private static final int RECOVER_FROM_INVALID_GLOBALCONF_DELAY = 60;
    private int nextDelay = 0;
    private int currentDelay = 0;
    private int prevDelay = 0;

    //flag for indicating backoff retry state
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
        // Use fibonacci number series for modeling backoff delay
        nextDelay = currentDelay + prevDelay;


        if (failed && nextDelay < OcspClientWorker.getNextOcspFetchIntervalSeconds()) {
            prevDelay = currentDelay;
            currentDelay = nextDelay;
            log.info("Next OCSP refresh retry scheduled in {} seconds", nextDelay);
            return FiniteDuration.create(nextDelay, TimeUnit.SECONDS);
        } else {
            log.info("Next OCSP refresh scheduled in {} seconds", OcspClientWorker.getNextOcspFetchIntervalSeconds());
            return FiniteDuration.create(
                    OcspClientWorker.getNextOcspFetchIntervalSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    private FiniteDuration getNextDelayForInvalidGlobalConf() {
        return FiniteDuration.create(RECOVER_FROM_INVALID_GLOBALCONF_DELAY, TimeUnit.SECONDS);
    }

    @Override
    public void onReceive(Object incoming) throws Exception {
        if (CANCEL.equals(incoming)) {
            log.debug("received message OcspClientWorker.CANCEL");
            cancelNextSend();
        } else if (RESCHEDULE.equals(incoming)) {
            log.debug("received message OcspClientWorker.RESCHEDULE");
            log.info("OCSP-response refresh cycle rescheduling");
            scheduleNextSend(getNextDelay());
        } else if (SUCCESS.equals(incoming)) {
            log.debug("received message OcspClientJob.SUCCESS");
            log.info("OCSP-response refresh cycle successfully completed, continuing with normal scheduling");
            failed = false;
            currentDelay = 0;
        } else if (FAILED.equals(incoming)) {
            log.debug("received message OcspClientJob.FAILED");
            if (!failed) {
                log.info("OCSP-response refresh cycle failed, switching to retry backoff schedule");
                // move into recover-from-failed state
                // cancel next send and start fibonacci-recovering
                cancelNextSend();
                failed = true;
                scheduleNextSend(getNextDelay());
            } else {
                // no need to touch scheduling, we have already
                // scheduled correctly in previous round's
                // VariableIntervalPeriodicJob.onReceive(EXECUTE)
                log.info("OCSP-response refresh retry failed, continuing along backoff schedule");
            }
        } else if (GLOBAL_CONF_INVALIDATED.equals(incoming)) {
            log.debug("received message OcspClientWorker.GLOBAL_CONF_INVALIDATED");
            log.info("OCSP-response refresh cycle failed due to invalid global configuration, "
                    + "switching to global configuration recovery schedule");
            // attempted to execute OCSP refresh, but global conf was
            // invalid at that time -> reschedule
            cancelNextSend();
            scheduleNextSend(getNextDelayForInvalidGlobalConf());
            failed = false;
            currentDelay = 0;

        } else {
            // received either EXECUTE (VariableIntervalPeriodicJob
            // executes, and schedules next EXECUTE) or something else
            // (which is dismissed in VariableIntervalPeriodicJob)
            super.onReceive(incoming);
        }
    }
}

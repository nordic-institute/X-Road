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
 * Periodically calls OcspClient reload
 */
@Slf4j
public class OcspClientReload extends VariableIntervalPeriodicJob {

    private static final int INTERVAL_SECONDS = 60;

    private static final FiniteDuration INITIAL_DELAY =
            FiniteDuration.create(100, TimeUnit.MILLISECONDS);

    OcspClientReload() {
        super(OCSP_CLIENT, OcspClientWorker.RELOAD);
    }

    @Override
    protected FiniteDuration getInitialDelay() {
        return INITIAL_DELAY;
    }

    @Override
    protected FiniteDuration getNextDelay() {
        return FiniteDuration.create(INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
}

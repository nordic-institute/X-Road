/*
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
package ee.ria.xroad.opmonitordaemon;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.SlidingTimeWindowReservoir;

import java.util.concurrent.TimeUnit;

/**
 * A counter metric that uses the sliding time window reservoir internally to
 * support periodic and configurable resetting.
 * This counter does not support manually decrementing the count (the dec()
 * methods raise a runtime exception).
 * We extend Counter instead of implementing the Metric and Counting
 * interfaces directly, in order to be able to register our metric objects with
 * the registry.
 */
class SlidingTimeWindowCounter extends Counter {

    private final Reservoir reservoir;

    /**
     * Creates a counter with the given window of time.
     *
     * @param window     the window of time
     * @param windowUnit the unit of {@code window}
     */
    SlidingTimeWindowCounter(long window, TimeUnit windowUnit) {
        reservoir = new SlidingTimeWindowReservoir(window, windowUnit);
    }

    /**
     * @return the snapshot size of the sliding time window reservoir.
     */
    @Override
    public long getCount() {
        return reservoir.getSnapshot().size();
    }

    @Override
    public void inc() {
        reservoir.update(1L);
    }

    @Override
    public void inc(long n) {
        for (int i = 0; i < n; ++i) {
            inc();
        }
    }

    @Override
    public void dec() {
        throw new RuntimeException("This counter can be incremented only");
    }

    @Override
    public void dec(long n) {
        throw new RuntimeException("This counter can be incremented only");
    }

}

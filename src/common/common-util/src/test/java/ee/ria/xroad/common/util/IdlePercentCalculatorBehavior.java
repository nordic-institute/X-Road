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
package ee.ria.xroad.common.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests to verify CPU idle percent calculator behavior.
 */
@Slf4j
public class IdlePercentCalculatorBehavior {

    /**
     * Just to see if we can get realistic idle percent from realistic data.
     */
    @Test
    public void shouldCalculateIdlePercent() {
        CpuStats previous = new CpuStats(
                27232919, 16826, 64593485, 188316115, 672027, 4, 4882, 0);
        CpuStats current = new CpuStats(
                27233052, 16826, 64593978, 188317718, 672029, 4, 4882, 0);
        Double idlePercent = IdlePercentCalculator.calculate(
                previous, current);
        assertNotNull(idlePercent);
        log.info("Idle percent: '{}'", idlePercent);
    }

    /**
     * Tests that the result is null if there's no previous result.
     */
    @Test
    public void shouldReturnNullIfPreviousCpuStatsIsNull() {
        CpuStats previous = null;
        CpuStats current = new CpuStats(
                27233052, 16826, 64593978, 188317718, 672029, 4, 4882, 0);
        Double idlePercent = IdlePercentCalculator.calculate(previous, current);

        assertNull(idlePercent);
    }

    /**
     * Tests that the result is null if there's no current result.
     */
    @Test
    public void shouldReturnNullIfCurrentCpuStatsIsNull() {
        CpuStats previous = new CpuStats(
                27232919, 16826, 64593485, 188316115, 672027, 4, 4882, 0);
        CpuStats current = null;
        Double idlePercent = IdlePercentCalculator.calculate(
                previous, current);

        assertNull(idlePercent);
    }
}

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

/**
 * Calculates idle percent the same way as UNIX 'top' utility does.
 */
public final class IdlePercentCalculator {

    private static final int MAX_SCALE = 100;

    private IdlePercentCalculator() {
    }

    /**
     * Calculates idle percent the same way as UNIX 'top' utility does.
     * @param previous previous CPU statistics
     * @param current current CPU statistics
     * @return CPU idle percentage
     */
    public static Double calculate(CpuStats previous, CpuStats current) {
        if (previous == null || current == null) {
            return null;
        }

        double userDiff = current.getUser() - previous.getUser();
        double niceDiff = current.getNice() - previous.getNice();
        double systemDiff = current.getSystem() - previous.getSystem();
        double idleDiff = getIdleDiff(previous, current);
        double iowaitDiff = current.getIowait() - previous.getIowait();
        double irqDiff = current.getIrq() - previous.getIrq();
        double softirqDiff = current.getSoftirq() - previous.getSoftirq();
        double stealDiff = current.getSteal() - previous.getSteal();

        double totalDiff = userDiff + niceDiff + systemDiff + idleDiff
                + iowaitDiff + irqDiff + softirqDiff + stealDiff;

        if (totalDiff < 1) {
            totalDiff = 1;
        }

        double scale = MAX_SCALE / totalDiff;

        return idleDiff * scale;
    }

    private static double getIdleDiff(CpuStats previous, CpuStats current) {
        double diff = current.getIdle() - previous.getIdle();

        return diff < 0 ? 0 : diff;
    }
}

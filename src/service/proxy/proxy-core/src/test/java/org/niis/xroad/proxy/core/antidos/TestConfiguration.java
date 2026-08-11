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
package org.niis.xroad.proxy.core.antidos;

class TestConfiguration extends AntiDosConfiguration {

    private final double maxCpuLoad;
    private final double maxHeapUsage;
    private final int maxParallelConnections;
    private final int minFreeFileHandles;
    private final boolean enabled;

    TestConfiguration(double maxCpuLoad, double maxHeapUsage, int maxParallelConnections,
                      int minFreeFileHandles, boolean enabled) {
        super(null);
        this.maxCpuLoad = maxCpuLoad;
        this.maxHeapUsage = maxHeapUsage;
        this.maxParallelConnections = maxParallelConnections;
        this.minFreeFileHandles = minFreeFileHandles;
        this.enabled = enabled;
    }

    static TestConfiguration of(int minFreeFileHandles, double maxCpuLoad) {
        return new TestConfiguration(maxCpuLoad, 1.1, 5000, minFreeFileHandles, true);
    }

    static TestConfiguration defaults() {
        return new TestConfiguration(0, 0, 0, 0, false);
    }

    @Override
    public double getMaxCpuLoad() {
        return maxCpuLoad;
    }

    @Override
    public double getMaxHeapUsage() {
        return maxHeapUsage;
    }

    @Override
    public int getMaxParallelConnections() {
        return maxParallelConnections;
    }

    @Override
    public int getMinFreeFileHandles() {
        return minFreeFileHandles;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }
}

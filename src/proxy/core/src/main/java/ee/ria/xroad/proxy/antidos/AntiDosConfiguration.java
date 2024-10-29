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
package ee.ria.xroad.proxy.antidos;

import ee.ria.xroad.common.SystemProperties;

class AntiDosConfiguration {

    /**
     * @return the number of allowed parallel connections
     */
    int getMaxParallelConnections() {
        return SystemProperties.getAntiDosMaxParallelConnections();
    }

    /**
     * @return the minimum number of free file handles required to process
     * an incoming connection after it has been accepted
     */
    int getMinFreeFileHandles() {
        return SystemProperties.getAntiDosMinFreeFileHandles();
    }

    /**
     * @return the maximum allowed CPU load. If the CPU load is more than this
     * value, incoming connection is not processed.
     */
    double getMaxCpuLoad() {
        return SystemProperties.getAntiDosMaxCpuLoad();
    }

    /**
     * @return the maximum allowed heap usage. If the heap usage is more than
     * this value, incoming connection is not processed.
     */
    double getMaxHeapUsage() {
        return SystemProperties.getAntiDosMaxHeapUsage();
    }
}

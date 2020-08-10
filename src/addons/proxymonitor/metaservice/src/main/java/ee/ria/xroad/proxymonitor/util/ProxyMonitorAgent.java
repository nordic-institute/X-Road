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
package ee.ria.xroad.proxymonitor.util;

import ee.ria.xroad.common.util.SystemMetrics;
import ee.ria.xroad.monitor.common.StatsRequest;
import ee.ria.xroad.monitor.common.StatsResponse;

import akka.actor.UntypedAbstractActor;
import com.sun.management.UnixOperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;

/**
 * Proxy monitoring agent
 */
@Slf4j
public class ProxyMonitorAgent extends UntypedAbstractActor {

    private boolean failureState = false;

    @Override
    public void onReceive(Object o) throws Throwable {
        if (o instanceof StatsRequest) {
            handleStatsRequest();
        }
    }

    private void handleStatsRequest() {
        final UnixOperatingSystemMXBean stats = SystemMetrics.getStats();
        try {
            final StatsResponse response = new StatsResponse(
                    stats.getOpenFileDescriptorCount(),
                    stats.getMaxFileDescriptorCount(),
                    Math.max(stats.getSystemCpuLoad(), 0d),
                    stats.getCommittedVirtualMemorySize(),
                    stats.getFreePhysicalMemorySize(),
                    stats.getTotalPhysicalMemorySize(),
                    stats.getFreeSwapSpaceSize(),
                    stats.getTotalSwapSpaceSize());
            failureState = false;
            sender().tell(response, self());
        } catch (InternalError ignored) {
            // Querying stats fails with an java.lang.InternalError if all file descriptors are in use
            // An uncaught InternalError (by default) stops the actorsystem and Akka forces the JVM to exit.
            if (!failureState) {
                //Avoid logging periodically during failure.
                log.error("Failed to retrieve OS stats", ignored);
                failureState = true;
            }
        }
    }
}

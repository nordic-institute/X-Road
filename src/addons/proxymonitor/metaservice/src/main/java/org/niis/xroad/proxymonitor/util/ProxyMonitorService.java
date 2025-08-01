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
package org.niis.xroad.proxymonitor.util;

import com.sun.management.UnixOperatingSystemMXBean;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.monitor.common.MonitorServiceGrpc;
import org.niis.xroad.monitor.common.StatsReq;
import org.niis.xroad.monitor.common.StatsResp;
import org.niis.xroad.proxy.core.util.SystemMetrics;

/**
 * Proxy monitoring agent
 */
@Slf4j
public class ProxyMonitorService extends MonitorServiceGrpc.MonitorServiceImplBase {

    private boolean failureState = false;

    @Override
    public void getStats(StatsReq request, StreamObserver<StatsResp> responseObserver) {
        try {
            responseObserver.onNext(handleStatsRequest());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private StatsResp handleStatsRequest() throws InternalError {
        final UnixOperatingSystemMXBean stats = SystemMetrics.getStats();
        try {
            final StatsResp response = StatsResp.newBuilder()
                    .setOpenFileDescriptorCount(stats.getOpenFileDescriptorCount())
                    .setMaxFileDescriptorCount(stats.getMaxFileDescriptorCount())
                    .setSystemCpuLoad(Math.max(stats.getCpuLoad(), 0d))
                    .setCommittedVirtualMemorySize(stats.getCommittedVirtualMemorySize())
                    .setFreePhysicalMemorySize(stats.getFreeMemorySize())
                    .setTotalPhysicalMemorySize(stats.getTotalMemorySize())
                    .setFreeSwapSpaceSize(stats.getFreeSwapSpaceSize())
                    .setTotalSwapSpaceSize(stats.getTotalSwapSpaceSize())
                    .build();

            failureState = false;
            return response;
        } catch (InternalError internalError) {
            if (!failureState) {
                //Avoid logging periodically during failure.
                log.error("Failed to retrieve OS stats", internalError);
                failureState = true;
            }
            throw internalError;
        }
    }
}

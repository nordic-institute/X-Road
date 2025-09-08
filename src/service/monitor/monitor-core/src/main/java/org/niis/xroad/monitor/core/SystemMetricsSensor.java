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
package org.niis.xroad.monitor.core;

import io.grpc.stub.StreamObserver;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.monitor.common.MonitorServiceGrpc;
import org.niis.xroad.monitor.common.StatsReq;
import org.niis.xroad.monitor.common.StatsResp;
import org.niis.xroad.monitor.core.common.SystemMetricNames;
import org.niis.xroad.proxy.proto.ProxyRpcChannelProperties;

/**
 * System metrics sensor collects information such as
 * memory, cpu, swap and file descriptors.
 */
@Slf4j
@ApplicationScoped
public class SystemMetricsSensor {
    private static final int SYSTEM_CPU_LOAD_MULTIPLIER = 100;

    private final RpcChannelFactory rpcChannelFactory;
    private final ProxyRpcChannelProperties rpcChannelProperties;

    private MonitorServiceGrpc.MonitorServiceStub monitorServiceStub;

    public SystemMetricsSensor(EnvMonitorProperties envMonitorProperties,
                               RpcChannelFactory rpcChannelFactory, ProxyRpcChannelProperties rpcChannelProperties) {
        this.rpcChannelFactory = rpcChannelFactory;
        this.rpcChannelProperties = rpcChannelProperties;
        log.info("Creating sensor, measurement interval: {}", envMonitorProperties.systemMetricsSensorInterval());
    }

    @PostConstruct
    public void afterPropertiesSet() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(), rpcChannelProperties.host(),
                rpcChannelProperties.port());
        var channel = rpcChannelFactory.createChannel(rpcChannelProperties);

        monitorServiceStub = MonitorServiceGrpc.newStub(channel).withWaitForReady();
    }

    /**
     * Update sensor metrics
     */
    private void updateMetrics(StatsResp stats) {
        MetricRegistryHolder registryHolder = MetricRegistryHolder.getInstance();
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.SYSTEM_CPU_LOAD)
                .update((long) (stats.getSystemCpuLoad() * SYSTEM_CPU_LOAD_MULTIPLIER));
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.FREE_PHYSICAL_MEMORY)
                .update(stats.getFreePhysicalMemorySize());
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.FREE_SWAP_SPACE)
                .update(stats.getFreeSwapSpaceSize());
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.OPEN_FILE_DESCRIPTOR_COUNT)
                .update(stats.getOpenFileDescriptorCount());
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.COMMITTED_VIRTUAL_MEMORY)
                .update(stats.getCommittedVirtualMemorySize());
        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.MAX_FILE_DESCRIPTOR_COUNT)
                .update(stats.getMaxFileDescriptorCount());
        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.TOTAL_SWAP_SPACE)
                .update(stats.getTotalSwapSpaceSize());
        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.TOTAL_PHYSICAL_MEMORY)
                .update(stats.getTotalPhysicalMemorySize());
    }

    @Scheduled(every = "${xroad.env-monitor.system-metrics-sensor-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            skipExecutionIf = Scheduled.ApplicationNotRunning.class)
    public void measure() {
        monitorServiceStub.getStats(StatsReq.getDefaultInstance(), new StreamObserver<>() {

            @Override
            public void onNext(StatsResp value) {
                updateMetrics(value);
            }

            @Override
            public void onError(Throwable t) {
                log.error("Failed to update system metrics stats. Rescheduling..", t);
            }

            @Override
            public void onCompleted() {
                //NO-OP
            }
        });
    }

}

/*
 * The MIT License
 *
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

package org.niis.xroad.monitor.rpc;

import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.common.MetricsServiceGrpc;
import org.niis.xroad.monitor.common.SystemMetricsReq;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class MonitorRpcClient extends AbstractRpcClient {

    private final RpcChannelFactory rpcChannelFactory;
    private final EnvMonitorRpcChannelProperties envMonitorRpcChannelProperties;

    private ManagedChannel channel;
    private MetricsServiceGrpc.MetricsServiceBlockingStub metricsServiceBlockingStub;

    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.MONITOR;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(), envMonitorRpcChannelProperties.host(),
                envMonitorRpcChannelProperties.port());
        channel = rpcChannelFactory.createChannel(envMonitorRpcChannelProperties);

        metricsServiceBlockingStub = MetricsServiceGrpc.newBlockingStub(channel).withWaitForReady();
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public MetricsGroup getMetrics(String... metricNames) {
        try {
            var response = exec(() -> metricsServiceBlockingStub.getMetrics(SystemMetricsReq.newBuilder()
                    .setIsClientOwner(true)
                    .addAllMetricNames(List.of(metricNames))
                    .build()));

            return response.getMetrics();
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError("Failed to get metrics for: " + Arrays.toString(metricNames), e);
        }
    }

    public MetricsGroup getMetrics(List<String> metricNames, boolean isOwner) {
        try {
            var response = exec(() -> metricsServiceBlockingStub.getMetrics(SystemMetricsReq.newBuilder()
                    .setIsClientOwner(isOwner)
                    .addAllMetricNames(metricNames)
                    .build()));

            return response.getMetrics();
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError("Failed to get metrics for: " + String.join(",", metricNames), e);
        }
    }

}

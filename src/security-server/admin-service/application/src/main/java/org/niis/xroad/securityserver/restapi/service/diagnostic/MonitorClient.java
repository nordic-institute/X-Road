/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.service.diagnostic;

import ee.ria.xroad.common.SystemProperties;

import io.grpc.Channel;
import lombok.Getter;
import org.niis.xroad.common.rpc.client.RpcClient;
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.common.MetricsServiceGrpc;
import org.niis.xroad.monitor.common.SystemMetricsReq;

import java.util.Arrays;
import java.util.List;

public class MonitorClient {
    private static final int TIMEOUT_AWAIT = 10 * 1000;
    private final RpcClient<MetricsRpcExecutionContext> metricsRpcClient;

    public MonitorClient() throws Exception {
        this.metricsRpcClient = RpcClient.newClient(SystemProperties.getGrpcInternalHost(),
                SystemProperties.getEnvMonitorPort(), TIMEOUT_AWAIT, MetricsRpcExecutionContext::new);
    }

    public MetricsGroup getMetrics(String... metricNames) {
        try {
            var response = metricsRpcClient.execute(ctx -> ctx.getMetricsServiceBlockingStub().getMetrics(SystemMetricsReq.newBuilder()
                    .setIsClientOwner(true)
                    .addAllMetricNames(List.of(metricNames))
                    .build()));

            return response.getMetrics();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get metrics for: " + Arrays.toString(metricNames), e);
        }
    }


    @Getter
    private static class MetricsRpcExecutionContext implements RpcClient.ExecutionContext {
        private final MetricsServiceGrpc.MetricsServiceBlockingStub metricsServiceBlockingStub;

        MetricsRpcExecutionContext(Channel channel) {
            metricsServiceBlockingStub = MetricsServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }
    }
}

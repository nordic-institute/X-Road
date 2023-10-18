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
package ee.ria.xroad.proxymonitor.util;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.proxymonitor.message.MetricSetType;

import io.grpc.Channel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.client.RpcClient;
import org.niis.xroad.monitor.common.MetricsServiceGrpc;
import org.niis.xroad.monitor.common.SystemMetricsReq;

import java.util.List;

/**
 * Created by hyoty on 25.9.2015.
 */
@Slf4j
public class MonitorClient {
    private static final int TIMEOUT_AWAIT = 10 * 1000;

    private final RpcClient<MetricsRpcExecutionContext> metricsRpcClient;

    public MonitorClient() throws Exception {
        this.metricsRpcClient = RpcClient.newClient(SystemProperties.getGrpcInternalHost(),
                SystemProperties.getEnvMonitorPort(), TIMEOUT_AWAIT, MetricsRpcExecutionContext::new);
    }

    /**
     * Get monitoring metrics
     */
    public MetricSetType getMetrics(List<String> metricNames, boolean isOwner) {
        try {
            var response = metricsRpcClient.execute(ctx -> ctx.getMetricsServiceBlockingStub().getMetrics(SystemMetricsReq.newBuilder()
                    .setIsClientOwner(isOwner)
                    .addAllMetricNames(metricNames)
                    .build()));

            return MetricTypes.of(response.getMetrics());
        } catch (Exception e) {
            log.warn("Unable to read metrics", e);
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, "Unable to read metrics");
        }
    }

    public void shutdown() {
        metricsRpcClient.shutdown();
    }

    @Getter
    private static class MetricsRpcExecutionContext implements RpcClient.ExecutionContext {
        private final MetricsServiceGrpc.MetricsServiceBlockingStub metricsServiceBlockingStub;

        MetricsRpcExecutionContext(Channel channel) {
            metricsServiceBlockingStub = MetricsServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }
    }


}

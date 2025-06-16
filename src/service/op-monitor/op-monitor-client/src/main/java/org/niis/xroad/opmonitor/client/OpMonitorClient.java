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
package org.niis.xroad.opmonitor.client;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import io.grpc.Channel;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.niis.xroad.common.rpc.client.RpcClient;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.ServiceIdMapper;
import org.niis.xroad.opmonitor.api.GetOperationalDataIntervalsReq;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringSystemProperties;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.opmonitor.api.SecurityServerType;

import java.time.Instant;
import java.util.List;

public class OpMonitorClient {
    private static final int TIMEOUT_AWAIT = 10 * 1000;
    private final RpcClient<OpMonitorRpcExecutionContext> opMonitorRpcClient;

    public OpMonitorClient() throws Exception {
        this.opMonitorRpcClient = RpcClient.newClient(OpMonitoringSystemProperties.getOpMonitorHost(),
                OpMonitoringSystemProperties.getOpMonitorGrpcPort(), TIMEOUT_AWAIT, OpMonitorRpcExecutionContext::new);
    }

    @PreDestroy
    public void destroy() {
        if (opMonitorRpcClient != null) {
            opMonitorRpcClient.shutdown();
        }
    }

    public List<OperationalDataInterval> getOperationalDataIntervals(Long recordsFrom,
                                                                     Long recordsTo,
                                                                     Integer interval,
                                                                     String securityServerType,
                                                                     ClientId memberId,
                                                                     ServiceId serviceId) {
        try {
            GetOperationalDataIntervalsReq.Builder reqBuilder = GetOperationalDataIntervalsReq.newBuilder()
                    .setRecordsFrom(recordsFrom)
                    .setRecordsTo(recordsTo)
                    .setIntervalInMinutes(interval);
            if (securityServerType != null) {
                reqBuilder.setSecurityServerType(SecurityServerType.valueOf(securityServerType.toUpperCase()));
            }
            if (memberId != null) {
                reqBuilder.setMemberId(ClientIdMapper.toDto(memberId));
            }
            if (serviceId != null) {
                reqBuilder.setServiceId(ServiceIdMapper.toDto(serviceId));
            }
            var response = opMonitorRpcClient.execute(ctx ->
                    ctx.getOpMonitorServiceBlockingStub().getOperationalDataIntervals(reqBuilder.build()));

            return response.getOperationalDataIntervalList().stream().map(OperationalDataInterval::new).toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get operational data from: %s, to: %s".formatted(Instant.ofEpochMilli(recordsFrom),
                    Instant.ofEpochMilli(recordsTo)), e);
        }
    }


    @Getter
    private static class OpMonitorRpcExecutionContext implements RpcClient.ExecutionContext {
        private final OpMonitorServiceGrpc.OpMonitorServiceBlockingStub opMonitorServiceBlockingStub;

        OpMonitorRpcExecutionContext(Channel channel) {
            opMonitorServiceBlockingStub = OpMonitorServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }
    }
}

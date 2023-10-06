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
package ee.ria.xroad.proxy.monotoring;

import ee.ria.xroad.proxy.monitoring.MonitorServiceGrpc;
import ee.ria.xroad.proxy.monitoring.Void;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import org.niis.xroad.common.rpc.client.RpcClient;

import java.util.Date;

/**
 * Default implementation of the monitor agent interface.
 */
public class DefaultMonitorAgentImpl implements MonitorAgentProvider, Shutdownable {

    private static final StreamObserver<Void> NOOP_OBSERVER = new StreamObserver<>() {
        @Override
        public void onNext(Void value) {
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
        }
    };

    private final RpcClient<RpcMonitorAgentContext> rpcClient;

    /**
     * Construct agent for accessing monitoring agent using the provided channel.
     */
    public DefaultMonitorAgentImpl(final RpcClient<RpcMonitorAgentContext> client) {
        this.rpcClient = client;
    }

    @Override
    public void success(MessageInfo messageInfo, Date startTime, Date endTime) {
        call(ctx -> ctx.getMonitorServiceStub().success(
                MessageMapper.successfulMessage(messageInfo, startTime, endTime),
                NOOP_OBSERVER));
    }

    @Override
    public void serverProxyFailed(MessageInfo messageInfo) {
        call(ctx -> ctx.getMonitorServiceStub().serverProxyFailed(
                MessageMapper.serverProxyFailed(messageInfo),
                NOOP_OBSERVER
        ));
    }

    @Override
    public void failure(MessageInfo messageInfo, String faultCode, String faultMessage) {
        call(ctx -> ctx.getMonitorServiceStub().failure(
                MessageMapper.faultInfo(messageInfo, faultCode, faultMessage),
                NOOP_OBSERVER
        ));
    }

    private void call(final RpcClient.AsyncRpcExecution<RpcMonitorAgentContext> grpcCall) {
        if (rpcClient != null) {
            rpcClient.executeAsync(grpcCall);
        }
    }


    @Override
    public void shutdown() {
        if (rpcClient != null) {
            rpcClient.shutdown();
        }
    }

    @Getter
    public static class RpcMonitorAgentContext implements RpcClient.ExecutionContext {
        private final MonitorServiceGrpc.MonitorServiceStub monitorServiceStub;

        RpcMonitorAgentContext(Channel channel) {
            monitorServiceStub = MonitorServiceGrpc.newStub(channel).withWaitForReady();
        }
    }
}

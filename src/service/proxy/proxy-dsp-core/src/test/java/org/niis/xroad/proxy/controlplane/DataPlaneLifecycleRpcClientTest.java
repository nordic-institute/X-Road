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

package org.niis.xroad.proxy.controlplane;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.edc.dataplanelifecycle.proto.DataFlowState;
import org.niis.xroad.edc.dataplanelifecycle.proto.DataPlaneLifecycleServiceGrpc;
import org.niis.xroad.edc.dataplanelifecycle.proto.ReportDataFlowStateRequest;
import org.niis.xroad.rpc.common.Empty;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPlaneLifecycleRpcClientTest {

    @Mock
    private RpcChannelFactory rpcChannelFactory;
    @Mock
    private ControlPlaneRpcChannelProperties channelProperties;

    private Server server;
    private ManagedChannel channel;
    private DataPlaneLifecycleRpcClient client;

    private final AtomicReference<ReportDataFlowStateRequest> received = new AtomicReference<>();
    private final CountDownLatch receivedLatch = new CountDownLatch(1);
    private volatile StatusRuntimeException configuredError;

    @BeforeEach
    void setUp() throws Exception {
        var mockService = new DataPlaneLifecycleServiceGrpc.DataPlaneLifecycleServiceImplBase() {
            @Override
            public void reportDataFlowState(ReportDataFlowStateRequest request, StreamObserver<Empty> responseObserver) {
                if (configuredError != null) {
                    responseObserver.onError(configuredError);
                    return;
                }
                received.set(request);
                receivedLatch.countDown();
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        };

        server = ServerBuilder.forPort(0).addService(mockService).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();

        when(channelProperties.host()).thenReturn("localhost");
        when(channelProperties.port()).thenReturn(server.getPort());
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(channel);

        client = new DataPlaneLifecycleRpcClient(rpcChannelFactory, channelProperties);
        client.init();
    }

    @AfterEach
    void tearDown() {
        client.close();
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    @Timeout(10)
    void reportStateSendsProcessIdAndStateToTheControlPlane() throws Exception {
        client.reportState("process-1", DataFlowStates.STARTED);

        assertThat(receivedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getProcessId()).isEqualTo("process-1");
        assertThat(received.get().getState()).isEqualTo(DataFlowState.STARTED);
    }

    @Test
    @Timeout(10)
    void reportStateMapsProvisionedToPrepared() throws Exception {
        client.reportState("process-2", DataFlowStates.PROVISIONED);

        assertThat(receivedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getState()).isEqualTo(DataFlowState.PREPARED);
    }

    @Test
    @Timeout(10)
    void reportStateMapsCompletedToCompleted() throws Exception {
        client.reportState("process-3", DataFlowStates.COMPLETED);

        assertThat(receivedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getState()).isEqualTo(DataFlowState.COMPLETED);
    }

    @Test
    void reportStateNeverThrowsEvenForAStateThatIsNotReported() {
        assertThatCode(() -> client.reportState("process-4", DataFlowStates.SUSPENDED)).doesNotThrowAnyException();
    }

    @Test
    void reportStateNeverThrowsWhenTheControlPlaneRejectsTheCall() {
        configuredError = new StatusRuntimeException(Status.INTERNAL);

        assertThatCode(() -> client.reportState("process-5", DataFlowStates.STARTED)).doesNotThrowAnyException();
    }

    @Test
    @Timeout(10)
    void reportStateReturnsPromptlyEvenWhenTheControlPlaneIsUnreachable() throws Exception {
        server.shutdownNow();
        server.awaitTermination(5, TimeUnit.SECONDS);

        var started = System.nanoTime();
        client.reportState("process-6", DataFlowStates.STARTED);
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }
}

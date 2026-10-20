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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.TestPortUtils;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.opmonitor.api.GetOperationalDataIntervalsReq;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OpMonitorRpcServiceIntervalsTest {
    private static final long FROM = 1_700_000_040_000L;

    private final InsecureRpcCredentialsConfigurer credentials = new InsecureRpcCredentialsConfigurer();
    private final OperationalDataRecordManager recordManager = mock(OperationalDataRecordManager.class);

    private RpcServer rpcServer;
    private ManagedChannel channel;
    private OpMonitorServiceGrpc.OpMonitorServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        int port = TestPortUtils.findRandomPort();
        rpcServer = new RpcServer("127.0.0.1", port, credentials.createServerCredentials(),
                builder -> builder.addService(new OpMonitorRpcService(recordManager, mock(OperationalDataStore.class),
                        mock(OperationalDataQueryService.class))));
        rpcServer.init();
        channel = new RpcChannelFactory(credentials).createChannel(channelProperties(port));
        stub = OpMonitorServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow();
        rpcServer.destroy();
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0",
            "-1, 0, 0",
            "35792, 0, 0",
            "1, 1, 0",
            "1, 0, 5999940000",
            "1440, 0, 8640000000000"
    })
    void invalidRequestIsRejectedWithInvalidArgumentBeforeTheDatabaseIsQueried(int interval, long fromOffset, long toOffset) {
        var request = request(FROM + fromOffset, FROM + toOffset, interval);

        assertThatThrownBy(() -> stub.getOperationalDataIntervals(request))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        verifyNoInteractions(recordManager);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 0, 0",
            "35791, 0, 0",
            "1, 0, 5999880000",
            "1, 30000, 5999939999"
    })
    void validRequestIsPassedToTheDatabaseQuery(int interval, long fromOffset, long toOffset) {
        var from = FROM + fromOffset;
        var to = FROM + toOffset;

        stub.getOperationalDataIntervals(request(from, to, interval));

        verify(recordManager).queryRequestMetricsDividedInIntervals(from, to, interval, null, null, null);
    }

    private static GetOperationalDataIntervalsReq request(long from, long to, int interval) {
        return GetOperationalDataIntervalsReq.newBuilder()
                .setRecordsFrom(from)
                .setRecordsTo(to)
                .setIntervalInMinutes(interval)
                .build();
    }

    private static RpcChannelProperties channelProperties(int port) {
        return new RpcChannelProperties() {
            @Override
            public String host() {
                return "127.0.0.1";
            }

            @Override
            public int port() {
                return port;
            }

            @Override
            public int deadlineAfter() {
                return 10_000;
            }
        };
    }
}

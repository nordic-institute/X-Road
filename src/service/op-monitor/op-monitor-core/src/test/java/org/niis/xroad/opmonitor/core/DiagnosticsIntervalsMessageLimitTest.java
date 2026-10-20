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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.opmonitor.api.GetOperationalDataIntervalsReq;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OperationalDataIntervalsRequestValidator;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The largest Diagnostics response, the interval counts of the most buckets a request may produce, with the largest
 * encoding of each bucket, reaches the caller over a channel that has the smallest accepted message limit.
 */
class DiagnosticsIntervalsMessageLimitTest {
    private static final long MAX_BUCKETS = OperationalDataIntervalsRequestValidator.MAX_BUCKETS;
    private static final long FROM = 1_700_000_040_000L;
    private static final Instant YEAR_ONE = Instant.parse("0001-01-01T00:00:00Z");

    private final InsecureRpcCredentialsConfigurer credentials = new InsecureRpcCredentialsConfigurer();
    private final OperationalDataRecordManager recordManager = mock(OperationalDataRecordManager.class);

    private RpcServer rpcServer;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        int port = TestPortUtils.findRandomPort();
        rpcServer = new RpcServer("127.0.0.1", port, credentials.createServerCredentials(),
                builder -> builder.addService(new OpMonitorRpcService(recordManager, mock(OperationalDataStore.class),
                        mock(OperationalDataQueryService.class))));
        rpcServer.init();
        channel = new RpcChannelFactory(credentials).createChannel(channelProperties(port));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow();
        rpcServer.destroy();
    }

    @Test
    void responseOfTheMaximumBucketCountWithLargestEncodingIsAcceptedAtTheMinimumChannelLimit() {
        long to = FROM + TimeUnit.MINUTES.toMillis(MAX_BUCKETS - 2);
        assertThat(OperationalDataIntervalsRequestValidator.bucketCount(FROM, to, 1)).isEqualTo(MAX_BUCKETS);
        when(recordManager.queryRequestMetricsDividedInIntervals(any(), any(), any(), any(), any(), any()))
                .thenReturn(largestEncodedBuckets());

        var response = OpMonitorServiceGrpc.newBlockingStub(channel).getOperationalDataIntervals(
                GetOperationalDataIntervalsReq.newBuilder().setRecordsFrom(FROM).setRecordsTo(to).setIntervalInMinutes(1).build());

        assertThat(response.getOperationalDataIntervalCount()).isEqualTo(MAX_BUCKETS);
        assertThat(response.getSerializedSize())
                .isGreaterThan(CommonRpcConfigKeys.MIN_OP_MONITOR_CHANNEL_MESSAGE_SIZE * 8 / 10)
                .isLessThanOrEqualTo(CommonRpcConfigKeys.MIN_OP_MONITOR_CHANNEL_MESSAGE_SIZE);
    }

    private static List<OperationalDataInTimeInterval> largestEncodedBuckets() {
        return LongStream.range(0, MAX_BUCKETS)
                .mapToObj(i -> new OperationalDataInTimeInterval(YEAR_ONE.plusSeconds(60 * i), Long.MAX_VALUE, Long.MAX_VALUE))
                .toList();
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
                return 30_000;
            }

            @Override
            public Optional<Integer> maxInboundMessageSize() {
                return Optional.of(CommonRpcConfigKeys.MIN_OP_MONITOR_CHANNEL_MESSAGE_SIZE);
            }
        };
    }
}

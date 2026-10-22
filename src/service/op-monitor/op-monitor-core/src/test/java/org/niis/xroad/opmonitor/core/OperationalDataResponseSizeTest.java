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

import ee.ria.xroad.common.identifier.ClientId;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.QueryOperationalDataReq;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;
import org.niis.xroad.opmonitor.core.SyntheticOperationalData.Variant;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.OBJECT_READER;

/**
 * Measures the size of a query response over a real database, the query service, JSON, gzip, multipart and a real
 * channel, and checks the channel limit against it.
 */
class OperationalDataResponseSizeTest extends BaseTestUsingDB {
    private static final int RECORDS = 10_000;
    private static final int BATCH_SIZE = 100;
    private static final int SMALL_RESPONSE_RECORDS = 100;
    private static final long WINDOW_START = 1_234_500_000L;
    private static final int UNLIMITED = 256 * 1024 * 1024;
    private static final int MIB = 1024 * 1024;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final String LOOPBACK = "127.0.0.1";
    private static final String OPERATIONAL_DATA_REQUEST = "src/test/resources/operationaldata.request";
    private static final String QUERY_CONTENT_TYPE = "text/xml; charset=utf-8";
    private static final String FIXTURE_RECORDS_FROM = "<om:recordsFrom>1477468193</om:recordsFrom>";
    private static final String FIXTURE_RECORDS_TO = "<om:recordsTo>1477468205</om:recordsTo>";
    private static final ClientId.Conf QUERY_CLIENT = ClientId.Conf.create("XTEE-CI-XM", "GOV", "00000001", "System1");
    private static final Pattern RECORDS_COUNT = Pattern.compile("<om:recordsCount>(\\d+)</om:recordsCount>");
    private static final String SIZE_REJECTION = "gRPC message exceeds maximum size";

    private final InsecureRpcCredentialsConfigurer credentials = new InsecureRpcCredentialsConfigurer();
    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
    private final List<ManagedChannel> channels = new ArrayList<>();
    private Server grpcServer;

    record Measurement(Variant variant, int recordCount, int serializedSize, int multipartBytes, int gzipBytes, int jsonBytes,
                       long elapsedMillis) {
        String report() {
            return "op-monitor query response measurement: variant=%s records=%d serializedGrpcBytes=%d multipartBytes=%d"
                    .formatted(variant, recordCount, serializedSize, multipartBytes)
                    + " gzipAttachmentBytes=%d jsonBytes=%d elapsedMs=%d".formatted(gzipBytes, jsonBytes, elapsedMillis);
        }
    }

    @BeforeEach
    void startQueryService() throws Exception {
        DATABASE_CTX.doInTransaction(session -> session.createMutationQuery("delete OperationalDataRecordEntity").executeUpdate());
        var extensions = mock(GlobalConfExtensions.class);
        when(extensions.getMonitoringClient()).thenReturn(QUERY_CLIENT);
        when(globalConfProvider.getGlobalConfExtensions()).thenReturn(extensions);
        var healthDataRegistry = new HealthDataRegistry(new HealthDataMetrics(OP_MONITOR_PROPERTIES));
        var queryService = new OperationalDataQueryService(globalConfProvider, healthDataRegistry, operationalDataRecordManager,
                OP_MONITOR_PROPERTIES);
        var service = new OpMonitorRpcService(operationalDataRecordManager, mock(OperationalDataStore.class), queryService);
        grpcServer = NettyServerBuilder.forAddress(new InetSocketAddress(LOOPBACK, 0), credentials.createServerCredentials())
                .addService(service)
                .build()
                .start();
    }

    @AfterEach
    void stopQueryService() throws InterruptedException {
        channels.forEach(ManagedChannel::shutdownNow);
        grpcServer.shutdownNow().awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void tenThousandRecordResponseIsMeasured(Variant variant) throws Exception {
        var measurement = measure(variant, RECORDS);

        System.out.println(measurement.report());
        assertThat(measurement.recordCount()).isEqualTo(RECORDS);
        assertThat(measurement.gzipBytes()).isLessThan(measurement.jsonBytes());
        assertThat(measurement.serializedSize()).isGreaterThan(measurement.multipartBytes());
        assertThat(measurement.multipartBytes()).isGreaterThan(measurement.gzipBytes());
        assertThat(measurement.serializedSize()).isBetween(MIB / 4, 16 * MIB);
    }

    @Test
    void pessimisticResponseIsLargerThanTypicalResponse() throws Exception {
        var typical = measure(Variant.TYPICAL, RECORDS);
        clearRecords();
        var pessimistic = measure(Variant.PESSIMISTIC, RECORDS);

        assertThat(pessimistic.serializedSize()).isGreaterThan(typical.serializedSize());
    }

    @Test
    void defaultLimitIsAtLeastTwiceThePessimisticTenThousandRecordResponse() throws Exception {
        var pessimistic = measure(Variant.PESSIMISTIC, RECORDS);

        int defaultLimit = CommonRpcConfigKeys.CHANNEL_OP_MONITOR_MAX_INBOUND_MESSAGE_SIZE.convertedDefaultValue();

        assertThat((long) defaultLimit).isGreaterThanOrEqualTo(2L * pessimistic.serializedSize());
        assertThat(defaultLimit).isGreaterThanOrEqualTo(CommonRpcConfigKeys.MIN_OP_MONITOR_CHANNEL_MESSAGE_SIZE);
    }

    @Test
    void responseOfExactlyTheLimitIsAcceptedAndOneByteMoreIsRejected() throws Exception {
        storeRecords(Variant.TYPICAL, SMALL_RESPONSE_RECORDS);
        int responseSize = query(UNLIMITED).getSerializedSize();

        assertThat(query(responseSize).getSerializedSize()).isEqualTo(responseSize);
        assertThatThrownBy(() -> query(responseSize - 1))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
                    assertThat(e.getStatus().getDescription()).contains(SIZE_REJECTION)
                            .contains(String.valueOf(responseSize - 1)).contains(String.valueOf(responseSize));
                });
    }

    private Measurement measure(Variant variant, int recordCount) throws Exception {
        storeRecords(variant, recordCount);

        long started = System.nanoTime();
        var response = query(UNLIMITED);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        var body = response.getResponse().toByteArray();
        var gzip = gzipAttachment(response.getContentType(), body);
        var json = gunzip(gzip);
        int jsonRecords = OBJECT_READER.forType(OperationalDataRecords.class).<OperationalDataRecords>readValue(json).size();
        assertThat(soapRecordsCount(body)).isEqualTo(jsonRecords);
        return new Measurement(variant, jsonRecords, response.getSerializedSize(), body.length, gzip.length, json.length,
                elapsedMillis);
    }

    private void clearRecords() {
        DATABASE_CTX.doInTransaction(session -> session.createMutationQuery("delete OperationalDataRecordEntity").executeUpdate());
    }

    private void storeRecords(Variant variant, int recordCount) {
        var records = SyntheticOperationalData.records(variant, recordCount);
        for (int i = 0; i * BATCH_SIZE < recordCount; i++) {
            operationalDataRecordManager.storeRecords(records.subList(i * BATCH_SIZE, Math.min(recordCount, (i + 1) * BATCH_SIZE)),
                    WINDOW_START + i);
        }
    }

    private QueryOperationalDataResp query(int maxInboundMessageSize) throws IOException {
        var channel = new RpcChannelFactory(credentials).createChannel(channelProperties(maxInboundMessageSize));
        channels.add(channel);
        return OpMonitorServiceGrpc.newBlockingStub(channel).queryOperationalData(QueryOperationalDataReq.newBuilder()
                .setSoapQuery(ByteString.copyFrom(operationalDataQuery()))
                .setContentType(QUERY_CONTENT_TYPE)
                .build());
    }

    private static byte[] operationalDataQuery() throws IOException {
        return new String(Files.readAllBytes(Path.of(OPERATIONAL_DATA_REQUEST)), StandardCharsets.UTF_8)
                .replace(FIXTURE_RECORDS_FROM, "<om:recordsFrom>" + WINDOW_START + "</om:recordsFrom>")
                .replace(FIXTURE_RECORDS_TO, "<om:recordsTo>" + (WINDOW_START + RECORDS) + "</om:recordsTo>")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] gzipAttachment(String contentType, byte[] multipart) {
        var text = new String(multipart, StandardCharsets.ISO_8859_1);
        var attachmentStart = "content-id: <" + QueryResponses.ATTACHMENT_CONTENT_ID + ">\r\n\r\n";
        int start = text.indexOf(attachmentStart) + attachmentStart.length();
        int end = text.lastIndexOf("\r\n--" + QueryResponses.boundary(contentType) + "--");
        return text.substring(start, end).getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }

    private static int soapRecordsCount(byte[] multipart) {
        var matcher = RECORDS_COUNT.matcher(new String(multipart, StandardCharsets.ISO_8859_1));
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private RpcChannelProperties channelProperties(int maxInboundMessageSize) {
        return new RpcChannelProperties() {
            @Override
            public String host() {
                return LOOPBACK;
            }

            @Override
            public int port() {
                return grpcServer.getPort();
            }

            @Override
            public int deadlineAfter() {
                return 120_000;
            }

            @Override
            public Optional<Integer> maxInboundMessageSize() {
                return Optional.of(maxInboundMessageSize);
            }
        };
    }
}

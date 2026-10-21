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
import ee.ria.xroad.common.util.MimeTypes;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.QueryOperationalDataReq;
import org.niis.xroad.opmonitor.core.config.OpMonitorProperties;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.OBJECT_READER;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.formatFullOperationalDataAsJson;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.storeFullOperationalDataRecord;

/**
 * Starts the gRPC query method of op-monitor over a database and health state that do not depend on the clock,
 * and prepares the queries of the {@link QueryCase}s.
 */
abstract class AbstractQueryTest extends BaseTestUsingDB {
    static final String PROXY_SOAP_CONTENT_TYPE = MimeTypes.TEXT_XML_UTF8;

    private static final String LOOPBACK = "127.0.0.1";
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final String HEALTH_DATA_REQUEST = "src/test/resources/healthdata.request";
    private static final String OPERATIONAL_DATA_REQUEST = "src/test/resources/operationaldata.request";
    private static final String FIXTURE_RECORDS_FROM = "<om:recordsFrom>1477468193</om:recordsFrom>";
    private static final String FIXTURE_RECORDS_TO = "<om:recordsTo>1477468205</om:recordsTo>";
    private static final String MIME_BOUNDARY = "requestBoundary";
    private static final long EMPTY_WINDOW_START = 1_234_400_000L;
    private static final long RECORDS_WINDOW_START = 1_234_500_000L;
    private static final long TRUNCATED_WINDOW_START = 1_234_600_000L;
    private static final int TRUNCATED_RECORDS_IN_PAYLOAD = 2;
    private static final int TRUNCATED_RECORDS_STORED = 3;
    private static final long WINDOW_LENGTH = 10L;
    private static final long HEALTH_STARTUP_TIMESTAMP = 1_700_000_000_000L;
    private static final ClientId.Conf QUERY_CLIENT = ClientId.Conf.create("XTEE-CI-XM", "GOV", "00000001", "System1");
    private static final ClientId.Conf SERVICE_PROVIDER = ClientId.Conf.create("XTEE-CI-XM", "GOV", "00000000", "Center");

    enum QueryCase {
        HEALTH_DATA,
        OPERATIONAL_DATA_EMPTY,
        OPERATIONAL_DATA_WITH_RECORDS,
        OPERATIONAL_DATA_TRUNCATED,
        FAULT_BAD_CONTENT_TYPE,
        FAULT_MIME_ENCODED_QUERY,
        FAULT_INVALID_SOAP,
        FAULT_MISSING_CLIENT,
        FAULT_UNKNOWN_SERVICE,
        FAULT_INVALID_GLOBAL_CONF
    }

    record Query(byte[] body, String contentType) {
    }

    record Answer(String contentType, byte[] body) {
    }

    protected final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
    protected final HealthDataMetrics healthDataMetrics = new HealthDataMetrics(OP_MONITOR_PROPERTIES);
    protected final MetricRegistry healthMetricRegistry = new MetricRegistry();
    protected final HealthDataRegistry healthDataRegistry = mock(HealthDataRegistry.class);
    protected OperationalDataStore operationalDataStore;

    private final InsecureRpcCredentialsConfigurer credentials = new InsecureRpcCredentialsConfigurer();
    private Server grpcServer;
    private ManagedChannel channel;
    private OpMonitorServiceGrpc.OpMonitorServiceBlockingStub stub;

    @BeforeEach
    void startGrpcQueryMethod() throws Exception {
        DATABASE_CTX.doInTransaction(session -> session.createMutationQuery("delete OperationalDataRecordEntity").executeUpdate());
        when(healthDataRegistry.getRegistry()).thenReturn(healthMetricRegistry);
        healthDataMetrics.registerInitialMetrics(healthMetricRegistry, () -> HEALTH_STARTUP_TIMESTAMP);
        healthDataMetrics.processRecords(healthMetricRegistry, List.of(producerRecord(true), producerRecord(false)));

        startGrpcQueryMethodOverRecordManager();
    }

    private void startGrpcQueryMethodOverRecordManager() throws Exception {
        operationalDataStore = new OperationalDataStore(operationalDataRecordManager, healthDataMetrics, healthDataRegistry);
        var queryService = new OperationalDataQueryService(globalConfProvider, healthDataRegistry, operationalDataRecordManager,
                OP_MONITOR_PROPERTIES);
        var service = new OpMonitorRpcService(operationalDataRecordManager, operationalDataStore, queryService);
        grpcServer = NettyServerBuilder.forAddress(new InetSocketAddress(LOOPBACK, 0), credentials.createServerCredentials())
                .addService(service)
                .build()
                .start();
        channel = new RpcChannelFactory(credentials).createChannel(channelProperties(grpcServer.getPort()));
        stub = OpMonitorServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopGrpcQueryMethod() throws InterruptedException {
        channel.shutdownNow();
        grpcServer.shutdownNow().awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Restarts the gRPC query method over a record manager of its own that truncates responses at the given number of
     * records. The properties instance is private to the test, so no other test sees the limit. Must be called before
     * the first query of the test.
     */
    private void limitRecordsInPayload(int maxRecordsInPayload) throws Exception {
        var properties = new OpMonitorProperties(XRoadConfigBuilder.create()
                .register(OpMonitorConfigKeys.instance())
                .overrides(Map.of("xroad.op-monitor.max-records-in-payload", String.valueOf(maxRecordsInPayload)))
                .build());
        stopGrpcQueryMethod();
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, properties);
        startGrpcQueryMethodOverRecordManager();
    }

    Query prepareQuery(QueryCase queryCase) throws Exception {
        return switch (queryCase) {
            case HEALTH_DATA -> new Query(read(HEALTH_DATA_REQUEST), PROXY_SOAP_CONTENT_TYPE);
            case OPERATIONAL_DATA_EMPTY -> new Query(operationalDataQuery(EMPTY_WINDOW_START), PROXY_SOAP_CONTENT_TYPE);
            case OPERATIONAL_DATA_WITH_RECORDS -> {
                storeFullOperationalDataRecord(RECORDS_WINDOW_START + 1, QUERY_CLIENT, SERVICE_PROVIDER, operationalDataRecordManager);
                storeFullOperationalDataRecord(RECORDS_WINDOW_START + 2, QUERY_CLIENT, SERVICE_PROVIDER, operationalDataRecordManager);
                yield new Query(operationalDataQuery(RECORDS_WINDOW_START), PROXY_SOAP_CONTENT_TYPE);
            }
            case OPERATIONAL_DATA_TRUNCATED -> {
                limitRecordsInPayload(TRUNCATED_RECORDS_IN_PAYLOAD);
                for (int i = 1; i <= TRUNCATED_RECORDS_STORED; i++) {
                    storeFullOperationalDataRecord(TRUNCATED_WINDOW_START + i, QUERY_CLIENT, SERVICE_PROVIDER,
                            operationalDataRecordManager);
                }
                yield new Query(operationalDataQuery(TRUNCATED_WINDOW_START), PROXY_SOAP_CONTENT_TYPE);
            }
            case FAULT_BAD_CONTENT_TYPE -> new Query(read(HEALTH_DATA_REQUEST), "text/plain; charset=UTF-8");
            case FAULT_MIME_ENCODED_QUERY -> mimeEncodedHealthDataQuery();
            case FAULT_INVALID_SOAP -> new Query("not a SOAP message".getBytes(StandardCharsets.UTF_8), PROXY_SOAP_CONTENT_TYPE);
            case FAULT_MISSING_CLIENT -> new Query(new String(read(HEALTH_DATA_REQUEST), StandardCharsets.UTF_8)
                    .replaceAll("(?s)<xroad:client .*?</xroad:client>", "").getBytes(StandardCharsets.UTF_8),
                    PROXY_SOAP_CONTENT_TYPE);
            case FAULT_UNKNOWN_SERVICE -> new Query(new String(read(HEALTH_DATA_REQUEST), StandardCharsets.UTF_8)
                    .replace("<id:serviceCode>getSecurityServerHealthData<", "<id:serviceCode>getSecurityServerUnknownData<")
                    .getBytes(StandardCharsets.UTF_8),
                    PROXY_SOAP_CONTENT_TYPE);
            case FAULT_INVALID_GLOBAL_CONF -> {
                doThrow(XrdRuntimeException.systemInternalError("Global configuration is expired"))
                        .when(globalConfProvider).verifyValidity();
                yield new Query(read(HEALTH_DATA_REQUEST), PROXY_SOAP_CONTENT_TYPE);
            }
        };
    }

    Query operationalDataQueryWithInvertedTimeRange() throws IOException {
        return new Query(new String(read(OPERATIONAL_DATA_REQUEST), StandardCharsets.UTF_8)
                .replace(FIXTURE_RECORDS_FROM, "<om:recordsFrom>" + (EMPTY_WINDOW_START + WINDOW_LENGTH) + "</om:recordsFrom>")
                .replace(FIXTURE_RECORDS_TO, "<om:recordsTo>" + EMPTY_WINDOW_START + "</om:recordsTo>")
                .getBytes(StandardCharsets.UTF_8), PROXY_SOAP_CONTENT_TYPE);
    }

    Answer queryOverGrpc(Query query) {
        var response = stub.queryOperationalData(QueryOperationalDataReq.newBuilder()
                .setSoapQuery(ByteString.copyFrom(query.body()))
                .setContentType(query.contentType())
                .build());
        return new Answer(response.getContentType(), response.getResponse().toByteArray());
    }

    private static Query mimeEncodedHealthDataQuery() throws IOException {
        var message = "--" + MIME_BOUNDARY + "\r\ncontent-type: text/xml; charset=UTF-8\r\n\r\n"
                + new String(read(HEALTH_DATA_REQUEST), StandardCharsets.UTF_8) + "\r\n--" + MIME_BOUNDARY + "--\r\n";
        return new Query(message.getBytes(StandardCharsets.UTF_8),
                "multipart/related; type=\"text/xml\"; charset=UTF-8; boundary=" + MIME_BOUNDARY);
    }

    private static byte[] operationalDataQuery(long windowStart) throws IOException {
        return new String(read(OPERATIONAL_DATA_REQUEST), StandardCharsets.UTF_8)
                .replace(FIXTURE_RECORDS_FROM, "<om:recordsFrom>" + windowStart + "</om:recordsFrom>")
                .replace(FIXTURE_RECORDS_TO, "<om:recordsTo>" + (windowStart + WINDOW_LENGTH) + "</om:recordsTo>")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] read(String file) throws IOException {
        return Files.readAllBytes(Path.of(file));
    }

    private static OperationalDataRecord producerRecord(boolean succeeded) {
        OperationalDataRecord operationalData = OBJECT_READER.forType(OperationalDataRecord.class)
                .readValue(formatFullOperationalDataAsJson());
        operationalData.setSecurityServerType(OpMonitoringData.SecurityServerType.PRODUCER.getTypeString());
        operationalData.setServiceMemberCode("00000001");
        operationalData.setServiceSubsystemCode("System1");
        operationalData.setSucceeded(succeeded);
        operationalData.setRequestSize(999L);
        operationalData.setResponseSize(888L);
        return operationalData;
    }

    private static RpcChannelProperties channelProperties(int port) {
        return new RpcChannelProperties() {
            @Override
            public String host() {
                return LOOPBACK;
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

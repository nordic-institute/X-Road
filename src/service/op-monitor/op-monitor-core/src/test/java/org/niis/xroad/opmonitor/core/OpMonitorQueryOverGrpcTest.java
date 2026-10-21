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
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.AccessScope;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.QueryOperationalDataReq;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVER_PROXY_OPMONITOR_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.OBJECT_READER;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.formatFullOperationalDataAsJson;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.storeFullOperationalDataRecord;

/**
 * Queries answered through the gRPC method over a database and health data.
 */
class OpMonitorQueryOverGrpcTest extends BaseTestUsingDB {
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final String HEALTH_DATA_REQUEST = "src/test/resources/healthdata.request";
    private static final String OPERATIONAL_DATA_REQUEST = "src/test/resources/operationaldata.request";
    private static final String HEALTH_DATA_RESPONSE_ELEMENT = "getSecurityServerHealthDataResponse";
    private static final String QUERY_CONTENT_TYPE = "text/xml; charset=utf-8";
    private static final String FIXTURE_RECORDS_FROM = "<om:recordsFrom>1477468193</om:recordsFrom>";
    private static final String FIXTURE_RECORDS_TO = "<om:recordsTo>1477468205</om:recordsTo>";
    private static final long WINDOW_START = 1_234_500_000L;
    private static final ClientId.Conf CALLER = ClientId.Conf.create("XTEE-CI-XM", "GOV", "00000001", "System1");
    private static final ClientId.Conf OTHER_CLIENT = ClientId.Conf.create("XTEE-CI-XM", "GOV", "00000002", "System2");
    private static final ClientId.Conf PROVIDER = ClientId.Conf.create("XTEE-CI-XM", "GOV", "00000000", "Center");
    private static final String INTERNAL_IP_FIELD = "securityServerInternalIp";
    private static final String INTERNAL_IP = "192.168.3.250";
    private static final Pattern RECORDS_COUNT = Pattern.compile("<om:recordsCount>(\\d+)</om:recordsCount>");

    private final InsecureRpcCredentialsConfigurer credentials = new InsecureRpcCredentialsConfigurer();
    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
    private final HealthDataMetrics healthDataMetrics = new HealthDataMetrics(OP_MONITOR_PROPERTIES);
    private final HealthDataRegistry healthDataRegistry = new HealthDataRegistry(healthDataMetrics);

    private io.grpc.Server grpcServer;
    private ManagedChannel channel;
    private OpMonitorServiceGrpc.OpMonitorServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        DATABASE_CTX.doInTransaction(session -> session.createMutationQuery("delete OperationalDataRecordEntity").executeUpdate());
        healthDataMetrics.processRecords(healthDataRegistry.getRegistry(), List.of(producerRecord(true), producerRecord(false)));

        var operationalDataStore = new OperationalDataStore(operationalDataRecordManager, healthDataMetrics, healthDataRegistry);
        var queryService = new OperationalDataQueryService(globalConfProvider, healthDataRegistry, operationalDataRecordManager,
                OP_MONITOR_PROPERTIES);
        startRpcServer(new OpMonitorRpcService(operationalDataRecordManager, operationalDataStore, queryService));
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow();
        grpcServer.shutdownNow().awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @CsvSource({
            HEALTH_DATA_REQUEST + ", text/xml",
            OPERATIONAL_DATA_REQUEST + ", multipart/related"
    })
    void responseIsTheQueriedDataAndNotAFault(String requestFile, String expectedBaseContentType) throws Exception {
        var query = Files.readAllBytes(Path.of(requestFile));

        var response = query(query, QUERY_CONTENT_TYPE, AccessScope.ACCESS_SCOPE_UNSPECIFIED);

        assertThat(MimeUtils.getBaseContentType(response.getContentType())).isEqualTo(expectedBaseContentType);
        assertThat(isFault(response.getResponse().toByteArray())).isFalse();
    }

    @Test
    void fullViewReturnsAllClientsRecordsAndCallerOnlyReturnsTheCallersOwn() throws Exception {
        storeFullOperationalDataRecord(WINDOW_START + 1, CALLER, PROVIDER, operationalDataRecordManager);
        storeFullOperationalDataRecord(WINDOW_START + 2, CALLER, PROVIDER, operationalDataRecordManager);
        storeFullOperationalDataRecord(WINDOW_START + 3, OTHER_CLIENT, PROVIDER, operationalDataRecordManager);
        var query = operationalDataQuery();

        var fullView = query(query, QUERY_CONTENT_TYPE, AccessScope.FULL_VIEW);
        var callerOnly = query(query, QUERY_CONTENT_TYPE, AccessScope.CALLER_ONLY);

        assertThat(recordsCount(fullView)).isEqualTo(3);
        assertThat(recordsCount(callerOnly)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 99})
    void callerOnlyUnspecifiedAndUnknownScopesReturnOnlyTheCallersRecords(int accessScopeValue) throws Exception {
        storeFullOperationalDataRecord(WINDOW_START + 1, CALLER, PROVIDER, operationalDataRecordManager);
        storeFullOperationalDataRecord(WINDOW_START + 2, OTHER_CLIENT, PROVIDER, operationalDataRecordManager);
        storeFullOperationalDataRecord(WINDOW_START + 3, OTHER_CLIENT, PROVIDER, operationalDataRecordManager);

        var response = queryWithScopeValue(operationalDataQuery(), accessScopeValue);

        assertThat(recordsCount(response)).isEqualTo(1);
    }

    @Test
    void internalIpIsShownInFullViewOnly() throws Exception {
        storeFullOperationalDataRecord(WINDOW_START + 1, CALLER, PROVIDER, operationalDataRecordManager);
        var query = operationalDataQuery();

        assertThat(recordsJson(queryWithScopeValue(query, AccessScope.FULL_VIEW_VALUE))).contains(INTERNAL_IP_FIELD, INTERNAL_IP);
        for (var scopeValue : List.of(AccessScope.CALLER_ONLY_VALUE, AccessScope.ACCESS_SCOPE_UNSPECIFIED_VALUE, 99)) {
            assertThat(recordsJson(queryWithScopeValue(query, scopeValue))).doesNotContain(INTERNAL_IP_FIELD, INTERNAL_IP);
        }
    }

    @Test
    void healthDataIsAnsweredFromTheHealthMetrics() throws Exception {
        var response = query(Files.readAllBytes(Path.of(HEALTH_DATA_REQUEST)), QUERY_CONTENT_TYPE,
                AccessScope.ACCESS_SCOPE_UNSPECIFIED);

        var body = new String(response.getResponse().toByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains(HEALTH_DATA_RESPONSE_ELEMENT).contains("<om:successfulRequestCount>1<");
    }

    @Test
    void accessScopeDoesNotChangeTheResponse() throws Exception {
        var query = Files.readAllBytes(Path.of(HEALTH_DATA_REQUEST));

        var unspecified = query(query, QUERY_CONTENT_TYPE, AccessScope.ACCESS_SCOPE_UNSPECIFIED);

        for (var scope : List.of(AccessScope.FULL_VIEW, AccessScope.CALLER_ONLY)) {
            assertThat(query(query, QUERY_CONTENT_TYPE, scope)).isEqualTo(unspecified);
        }
    }

    @Test
    void failureIsAnsweredWithSoapFaultAsPayload() throws Exception {
        var response = query(Files.readAllBytes(Path.of(HEALTH_DATA_REQUEST)), MimeTypes.TEXT_PLAIN_UTF8,
                AccessScope.ACCESS_SCOPE_UNSPECIFIED);

        assertThat(MimeUtils.getBaseContentType(response.getContentType())).isEqualTo(MimeTypes.TEXT_XML);
        var fault = new SoapParserImpl().parse(MimeTypes.TEXT_XML_UTF8, response.getResponse().newInput());
        assertThat(fault).isInstanceOfSatisfying(SoapFault.class, f -> {
            assertThat(f.getCode()).isEqualTo(SERVER_SERVER_PROXY_OPMONITOR_X + ".invalid_content_type");
            assertThat(f.getString()).contains("Invalid content type text/plain; charset=utf-8, expecting " + MimeTypes.TEXT_XML);
        });
    }

    private QueryOperationalDataResp query(byte[] query, String contentType, AccessScope accessScope) {
        return stub.queryOperationalData(QueryOperationalDataReq.newBuilder()
                .setSoapQuery(ByteString.copyFrom(query))
                .setContentType(contentType)
                .setAccessScope(accessScope)
                .build());
    }

    private QueryOperationalDataResp queryWithScopeValue(byte[] query, int accessScopeValue) {
        return stub.queryOperationalData(QueryOperationalDataReq.newBuilder()
                .setSoapQuery(ByteString.copyFrom(query))
                .setContentType(QUERY_CONTENT_TYPE)
                .setAccessScopeValue(accessScopeValue)
                .build());
    }

    private static String recordsJson(QueryOperationalDataResp response) throws IOException {
        var text = new String(response.getResponse().toByteArray(), StandardCharsets.ISO_8859_1);
        var attachmentStart = "content-id: <" + QueryResponses.ATTACHMENT_CONTENT_ID + ">\r\n\r\n";
        int start = text.indexOf(attachmentStart) + attachmentStart.length();
        int end = text.lastIndexOf("\r\n--" + QueryResponses.boundary(response.getContentType()) + "--");
        var gzip = text.substring(start, end).getBytes(StandardCharsets.ISO_8859_1);
        try (var in = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] operationalDataQuery() throws IOException {
        return new String(Files.readAllBytes(Path.of(OPERATIONAL_DATA_REQUEST)), StandardCharsets.UTF_8)
                .replace(FIXTURE_RECORDS_FROM, "<om:recordsFrom>" + WINDOW_START + "</om:recordsFrom>")
                .replace(FIXTURE_RECORDS_TO, "<om:recordsTo>" + (WINDOW_START + 10) + "</om:recordsTo>")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static int recordsCount(QueryOperationalDataResp response) {
        var matcher = RECORDS_COUNT.matcher(new String(response.getResponse().toByteArray(), StandardCharsets.ISO_8859_1));
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean isFault(byte[] response) {
        return new String(response, StandardCharsets.ISO_8859_1).contains("Fault>");
    }

    private void startRpcServer(OpMonitorRpcService service) throws IOException {
        grpcServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0), credentials.createServerCredentials())
                .addService(service)
                .build()
                .start();
        channel = new RpcChannelFactory(credentials).createChannel(channelProperties(grpcServer.getPort()));
        stub = OpMonitorServiceGrpc.newBlockingStub(channel);
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

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
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.opmonitor.api.AccessScope;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.QueryOperationalDataReq;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVER_PROXY_OPMONITOR_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.opmonitor.api.OpMonitoringDaemonEndpoints.QUERY_DATA_PATH;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.OBJECT_READER;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.formatFullOperationalDataAsJson;

/**
 * Queries answered through the gRPC method and through the HTTP query handler, over the same database
 * and health data.
 */
class OpMonitorQueryOverGrpcTest extends BaseTestUsingDB {
    private static final String HEALTH_DATA_REQUEST = "src/test/resources/healthdata.request";
    private static final String OPERATIONAL_DATA_REQUEST = "src/test/resources/operationaldata.request";
    private static final String HEALTH_DATA_RESPONSE_ELEMENT = "getSecurityServerHealthDataResponse";
    private static final String QUERY_CONTENT_TYPE = "text/xml; charset=utf-8";
    private static final String BOUNDARY_PATTERN = "xroad[A-Za-z]{30}";
    private static final String NORMALISED_BOUNDARY = "xroadBOUNDARY";

    private final InsecureRpcCredentialsConfigurer credentials = new InsecureRpcCredentialsConfigurer();
    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
    private final HealthDataMetrics healthDataMetrics = new HealthDataMetrics(OP_MONITOR_PROPERTIES);
    private final HealthDataRegistry healthDataRegistry = new HealthDataRegistry(healthDataMetrics);

    private RpcServer rpcServer;
    private ManagedChannel channel;
    private OpMonitorServiceGrpc.OpMonitorServiceBlockingStub stub;
    private Server httpServer;
    private int httpPort;

    @BeforeEach
    void setUp() throws Exception {
        DATABASE_CTX.doInTransaction(session -> session.createMutationQuery("delete OperationalDataRecordEntity").executeUpdate());
        when(globalConfProvider.getGlobalConfExtensions()).thenReturn(mock(GlobalConfExtensions.class));
        healthDataMetrics.processRecords(healthDataRegistry.getRegistry(), List.of(producerRecord(true), producerRecord(false)));

        var operationalDataStore = new OperationalDataStore(operationalDataRecordManager, healthDataMetrics, healthDataRegistry);
        var queryService = new OperationalDataQueryService(globalConfProvider, healthDataRegistry, operationalDataRecordManager,
                OP_MONITOR_PROPERTIES);
        startRpcServer(new OpMonitorRpcService(operationalDataRecordManager, operationalDataStore, queryService));
        startHttpServer(new OpMonitorDaemonRequestHandler(OP_MONITOR_PROPERTIES, globalConfProvider,
                healthDataRegistry.getRegistry(), operationalDataRecordManager, operationalDataStore));
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow();
        rpcServer.destroy();
        httpServer.stop();
    }

    @ParameterizedTest
    @CsvSource({
            HEALTH_DATA_REQUEST + ", text/xml",
            OPERATIONAL_DATA_REQUEST + ", multipart/related"
    })
    void responseIsIdenticalToTheHttpQueryHandlerResponse(String requestFile, String expectedBaseContentType) throws Exception {
        var query = Files.readAllBytes(Path.of(requestFile));

        var viaGrpc = query(query, QUERY_CONTENT_TYPE, AccessScope.ACCESS_SCOPE_UNSPECIFIED);
        var viaHttp = queryOverHttp(query, QUERY_CONTENT_TYPE);

        assertThat(MimeUtils.getBaseContentType(viaGrpc.getContentType())).isEqualTo(expectedBaseContentType);
        assertThat(isFault(viaGrpc.getResponse().toByteArray())).isFalse();
        assertThat(normalised(viaGrpc.getContentType())).isEqualTo(normalised(viaHttp.headers().firstValue("Content-Type").orElseThrow()));
        assertThat(normalised(viaGrpc.getResponse().toByteArray())).isEqualTo(normalised(viaHttp.body()));
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
            assertThat(f.getString()).contains("Invalid content type " + MimeTypes.TEXT_PLAIN_UTF8 + ", expecting " + MimeTypes.TEXT_XML);
        });
    }

    private QueryOperationalDataResp query(byte[] query, String contentType, AccessScope accessScope) {
        return stub.queryOperationalData(QueryOperationalDataReq.newBuilder()
                .setSoapQuery(ByteString.copyFrom(query))
                .setContentType(contentType)
                .setAccessScope(accessScope)
                .build());
    }

    private HttpResponse<byte[]> queryOverHttp(byte[] query, String contentType) throws IOException, InterruptedException {
        try (var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + QUERY_DATA_PATH))
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(query))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static boolean isFault(byte[] response) {
        return new String(response, StandardCharsets.ISO_8859_1).contains("Fault>");
    }

    private static String normalised(byte[] body) {
        return normalised(new String(body, StandardCharsets.ISO_8859_1));
    }

    private static String normalised(String value) {
        return value.replaceAll(BOUNDARY_PATTERN, NORMALISED_BOUNDARY);
    }

    private void startRpcServer(OpMonitorRpcService service) throws IOException {
        int port = TestPortUtils.findRandomPort();
        rpcServer = new RpcServer("127.0.0.1", port, credentials.createServerCredentials(), builder -> builder.addService(service));
        rpcServer.init();
        channel = new RpcChannelFactory(credentials).createChannel(channelProperties(port));
        stub = OpMonitorServiceGrpc.newBlockingStub(channel);
    }

    private void startHttpServer(OpMonitorDaemonRequestHandler handler) throws Exception {
        httpServer = new Server();
        var connector = new ServerConnector(httpServer);
        connector.setHost("127.0.0.1");
        httpServer.addConnector(connector);
        httpServer.setHandler(handler);
        httpServer.start();
        httpPort = connector.getLocalPort();
    }

    private static OperationalDataRecord producerRecord(boolean succeeded) {
        OperationalDataRecord record = OBJECT_READER.forType(OperationalDataRecord.class).readValue(formatFullOperationalDataAsJson());
        record.setSecurityServerType(OpMonitoringData.SecurityServerType.PRODUCER.getTypeString());
        record.setServiceMemberCode("00000001");
        record.setServiceSubsystemCode("System1");
        record.setSucceeded(succeeded);
        record.setRequestSize(999L);
        record.setResponseSize(888L);
        return record;
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

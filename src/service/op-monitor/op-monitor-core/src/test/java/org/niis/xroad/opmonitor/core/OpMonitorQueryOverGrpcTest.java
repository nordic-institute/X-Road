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
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.opmonitor.api.AccessScope;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.QueryOperationalDataReq;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVER_PROXY_OPMONITOR_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.OBJECT_READER;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.formatFullOperationalDataAsJson;

/**
 * Queries answered through the gRPC method over a database and health data.
 */
class OpMonitorQueryOverGrpcTest extends BaseTestUsingDB {
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final String HEALTH_DATA_REQUEST = "src/test/resources/healthdata.request";
    private static final String OPERATIONAL_DATA_REQUEST = "src/test/resources/operationaldata.request";
    private static final String HEALTH_DATA_RESPONSE_ELEMENT = "getSecurityServerHealthDataResponse";
    private static final String QUERY_CONTENT_TYPE = "text/xml; charset=utf-8";

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
        var globalConfExtensions = mock(GlobalConfExtensions.class);
        when(globalConfProvider.getGlobalConfExtensions()).thenReturn(globalConfExtensions);
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

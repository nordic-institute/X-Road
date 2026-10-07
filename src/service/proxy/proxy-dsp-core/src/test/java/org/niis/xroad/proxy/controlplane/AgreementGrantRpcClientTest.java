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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.ServiceIdMapper;
import org.niis.xroad.edc.agreementgrant.proto.AgreementGrantServiceGrpc;
import org.niis.xroad.edc.agreementgrant.proto.Grant;
import org.niis.xroad.edc.agreementgrant.proto.NoGrant;
import org.niis.xroad.edc.agreementgrant.proto.NoGrantReason;
import org.niis.xroad.edc.agreementgrant.proto.ResolveAgreementGrantRequest;
import org.niis.xroad.edc.agreementgrant.proto.ResolveAgreementGrantResponse;
import org.niis.xroad.edc.agreementgrant.proto.Scope;
import org.niis.xroad.proxy.core.configuration.ProxyAgreementTokenProperties;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementGrantRpcClientTest {

    private static final ClientId CONSUMER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ServiceId SERVICE = ServiceId.Conf.create("DEV", "COM", "333", "PROVIDER", "getData", "v1");

    @Mock
    private RpcChannelFactory rpcChannelFactory;
    @Mock
    private ControlPlaneRpcChannelProperties channelProperties;
    @Mock
    private ProxyAgreementTokenProperties agreementTokenProperties;

    private Server server;
    private ManagedChannel channel;
    private AgreementGrantRpcClient client;

    private final AtomicReference<ResolveAgreementGrantResponse> configuredResponse = new AtomicReference<>();
    private StatusRuntimeException configuredError;

    @BeforeEach
    void setUp() throws Exception {
        var mockService = new AgreementGrantServiceGrpc.AgreementGrantServiceImplBase() {
            @Override
            public void resolveAgreementGrant(ResolveAgreementGrantRequest request,
                                              StreamObserver<ResolveAgreementGrantResponse> responseObserver) {
                if (configuredError != null) {
                    responseObserver.onError(configuredError);
                } else {
                    responseObserver.onNext(configuredResponse.get());
                    responseObserver.onCompleted();
                }
            }
        };

        server = ServerBuilder.forPort(0).addService(mockService).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();

        when(channelProperties.host()).thenReturn("localhost");
        when(channelProperties.port()).thenReturn(server.getPort());
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(channel);
        when(agreementTokenProperties.grantLookupDeadline()).thenReturn(Duration.ofSeconds(5));

        client = new AgreementGrantRpcClient(rpcChannelFactory, channelProperties, agreementTokenProperties);
        client.init();
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void resolveSuccessReturnsTypedGrant() {
        configuredResponse.set(ResolveAgreementGrantResponse.newBuilder()
                .setGrant(Grant.newBuilder()
                        .setClientId(ClientIdMapper.toDto(CONSUMER))
                        .setServiceId(ServiceIdMapper.toDto(SERVICE))
                        .addScopes(Scope.newBuilder().setMethod("GET").setPath("/foo/*").build())
                        .build())
                .build());

        var result = client.resolveAgreementGrant("agreement-1");

        assertThat(result).isPresent();
        assertThat(result.get().client()).isEqualTo(CONSUMER);
        assertThat(result.get().service()).isEqualTo(SERVICE);
        assertThat(result.get().scope()).containsExactly(new AgreementTokenScope("GET", "/foo/*"));
    }

    @Test
    void resolveNoGrantReturnsEmpty() {
        configuredResponse.set(ResolveAgreementGrantResponse.newBuilder()
                .setNoGrant(NoGrant.newBuilder().setReason(NoGrantReason.SUBJECT_NOT_A_SUBSYSTEM).build())
                .build());

        assertThat(client.resolveAgreementGrant("agreement-1")).isEmpty();
    }

    @Test
    @Timeout(10)
    void resolveReturnsEmptyPromptlyWhenTheControlPlaneIsUnreachable() throws Exception {
        server.shutdownNow();
        server.awaitTermination(5, TimeUnit.SECONDS);

        var started = System.nanoTime();
        var result = client.resolveAgreementGrant("agreement-1");
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(result).isEmpty();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void resolveFailureReturnsEmptyRatherThanThrowing() {
        configuredError = new StatusRuntimeException(Status.INTERNAL);

        assertThat(client.resolveAgreementGrant("agreement-1")).isEmpty();
    }

    @Test
    void resolveWithMultipleScopeEntriesMapsAllOfThem() {
        var scopes = List.of(
                Scope.newBuilder().setMethod("GET").setPath("/foo/*").build(),
                Scope.newBuilder().setMethod("POST").setPath("/bar").build());
        configuredResponse.set(ResolveAgreementGrantResponse.newBuilder()
                .setGrant(Grant.newBuilder()
                        .setClientId(ClientIdMapper.toDto(CONSUMER))
                        .setServiceId(ServiceIdMapper.toDto(SERVICE))
                        .addAllScopes(scopes)
                        .build())
                .build());

        var result = client.resolveAgreementGrant("agreement-1");

        assertThat(result).isPresent();
        assertThat(result.get().scope()).containsExactly(
                new AgreementTokenScope("GET", "/foo/*"), new AgreementTokenScope("POST", "/bar"));
    }
}

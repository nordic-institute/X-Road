/*
 * The MIT License
 *
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

package org.niis.xroad.edc.extension.assetaccess.failure;

import com.sun.net.httpserver.HttpServer;
import dev.failsafe.RetryPolicy;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.connector.controlplane.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.controlplane.contract.negotiation.NegotiationProcessorsImpl;
import org.eclipse.edc.connector.controlplane.contract.observe.ContractNegotiationObservableImpl;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementVerificationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.defaults.storage.contractnegotiation.InMemoryContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.defaults.storage.transferprocess.InMemoryTransferProcessStore;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.transfer.observe.TransferProcessObservableImpl;
import org.eclipse.edc.connector.controlplane.transfer.processors.TransferProcessorsImpl;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.controlplane.iam.AudienceResolver;
import org.eclipse.edc.http.client.EdcHttpClientImpl;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.dsp.http.dispatcher.DspHttpRemoteMessageDispatcherImpl;
import org.eclipse.edc.protocol.spi.ProtocolWebhook;
import org.eclipse.edc.protocol.spi.ProtocolWebhookResolver;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.entity.ProtocolMessages;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.statemachine.retry.EntityRetryProcessConfiguration;
import org.eclipse.edc.statemachine.retry.EntityRetryProcessFactory;
import org.eclipse.edc.token.spi.TokenDecorator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives EDC's own DSP dispatcher, HTTP client and requesting-side processors for negotiation and transfer, then
 * classifies the stored entity. A change in the error text EDC stores breaks these tests.
 */
class NegotiationTransferTransportShapeTest {
    private static final String PROTOCOL = "dataspace-protocol-http:2025-1";
    private static final String CONTEXT_ID = "participant-1";
    private static final Duration SHORT_READ_TIMEOUT = Duration.ofMillis(200);

    @ParameterizedTest
    @EnumSource(Stage.class)
    void closedPortIsATransportFault(Stage stage) {
        var kind = stage.failAgainst(Fixture.standard(), "http://" + StubbedNetwork.REFUSING_HOST + "/dsp");

        assertThat(kind).isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @ParameterizedTest
    @EnumSource(Stage.class)
    void silentSocketIsATransportFault(Stage stage) throws Exception {
        try (var silentServer = new ServerSocket(0)) {
            var fixture = new Fixture(SHORT_READ_TIMEOUT, validCredentials(), _ -> Result.success("provider-1"), true);

            var kind = stage.failAgainst(fixture, "http://localhost:" + silentServer.getLocalPort() + "/dsp");

            assertThat(kind).isEqualTo(FailureKind.TRANSPORT_FAULT);
        }
    }

    @ParameterizedTest
    @EnumSource(Stage.class)
    void unresolvableHostIsATransportFault(Stage stage) {
        var kind = stage.failAgainst(Fixture.standard(), "http://" + StubbedNetwork.UNKNOWN_HOST + "/dsp");

        assertThat(kind).isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @ParameterizedTest
    @EnumSource(Stage.class)
    void serverErrorIsOther(Stage stage) throws Exception {
        var kind = failAgainstServerAnswering(stage, 503);

        assertThat(kind).isEqualTo(FailureKind.OTHER);
    }

    @ParameterizedTest
    @MethodSource("stagesAndClientErrorStatuses")
    void clientErrorIsAProviderRejection(Stage stage, int status) throws Exception {
        var kind = failAgainstServerAnswering(stage, status);

        assertThat(kind).isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @ParameterizedTest
    @EnumSource(Stage.class)
    void credentialsFailureIsOther(Stage stage) {
        var fixture = new Fixture(Duration.ofSeconds(5), Result.failure("no credentials"), _ -> Result.success("provider-1"), true);

        var kind = stage.failAgainst(fixture, "http://localhost:1/dsp");

        assertThat(kind).isEqualTo(FailureKind.OTHER);
    }

    @ParameterizedTest
    @EnumSource(Stage.class)
    void audienceFailureIsOther(Stage stage) {
        var fixture = new Fixture(Duration.ofSeconds(5), validCredentials(), _ -> Result.failure("no audience"), true);

        var kind = stage.failAgainst(fixture, "http://localhost:1/dsp");

        assertThat(kind).isEqualTo(FailureKind.OTHER);
    }

    @ParameterizedTest
    @EnumSource(Stage.class)
    void unregisteredMessageTypeIsOther(Stage stage) {
        var fixture = new Fixture(Duration.ofSeconds(5), validCredentials(), _ -> Result.success("provider-1"), false);

        var kind = stage.failAgainst(fixture, "http://localhost:1/dsp");

        assertThat(kind).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void verificationSendToAClosedPortWithAReceivedMessageIsATransportFault() {
        var kind = failVerificationAgainst("http://" + StubbedNetwork.REFUSING_HOST + "/dsp");

        assertThat(kind).isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void verificationSendAnsweredWithAClientErrorAndAReceivedMessageIsAProviderRejection() throws Exception {
        var server = serverAnswering(403);
        try {
            var kind = failVerificationAgainst("http://localhost:" + server.getAddress().getPort() + "/dsp");

            assertThat(kind).isEqualTo(FailureKind.PROVIDER_REJECTION);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void terminatedNegotiationWithAReceivedMessageAndNoErrorTextIsAProviderRejection() {
        var negotiation = negotiation(ContractNegotiationStates.TERMINATED, "http://localhost:1/dsp", receivedMessage());

        assertThat(negotiation.getErrorDetail()).isNull();
        assertThat(FailureClassifier.classify(negotiation)).isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    private static Stream<Arguments> stagesAndClientErrorStatuses() {
        return Stream.of(Stage.values()).flatMap(stage -> Stream.of(403, 404, 409).map(status -> Arguments.of(stage, status)));
    }

    private static FailureKind failAgainstServerAnswering(Stage stage, int status) throws IOException {
        var server = serverAnswering(status);
        try {
            return stage.failAgainst(Fixture.standard(), "http://localhost:" + server.getAddress().getPort() + "/dsp");
        } finally {
            server.stop(0);
        }
    }

    private static FailureKind failVerificationAgainst(String counterPartyAddress) {
        var fixture = Fixture.standard();
        var negotiation = negotiation(ContractNegotiationStates.VERIFYING, counterPartyAddress, receivedMessage());

        fixture.negotiationProcessors().processVerifying(negotiation).join();

        assertThat(negotiation.getState()).isEqualTo(ContractNegotiationStates.TERMINATING.code());
        return FailureClassifier.classify(negotiation);
    }

    private static HttpServer serverAnswering(int status) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static Result<TokenRepresentation> validCredentials() {
        return Result.success(TokenRepresentation.Builder.newInstance().token("token").build());
    }

    private static ProtocolMessages receivedMessage() {
        var messages = new ProtocolMessages();
        messages.addReceived("received-1");
        return messages;
    }

    private static ContractNegotiation negotiation(ContractNegotiationStates state, String counterPartyAddress,
                                                   ProtocolMessages protocolMessages) {
        var policy = Policy.Builder.newInstance().build();
        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("consumer-1")
                .contractSigningDate(0L)
                .assetId("asset-1")
                .policy(policy)
                .build();
        var offer = ContractOffer.Builder.newInstance().id("offer-1").assetId("asset-1").policy(policy).build();
        return ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol(PROTOCOL)
                .counterPartyId("provider-1")
                .counterPartyAddress(counterPartyAddress)
                .participantContextId(CONTEXT_ID)
                .contractOffer(offer)
                .contractAgreement(agreement)
                .protocolMessages(protocolMessages)
                .state(state.code())
                .stateCount(1)
                .build();
    }

    private static TransferProcess requestingTransfer(String counterPartyAddress) {
        return TransferProcess.Builder.newInstance()
                .id("tp-1")
                .protocol(PROTOCOL)
                .counterPartyAddress(counterPartyAddress)
                .contractId("contract-1")
                .participantContextId(CONTEXT_ID)
                .transferType("HttpData-PULL")
                .state(TransferProcessStates.REQUESTING.code())
                .stateCount(1)
                .build();
    }

    private enum Stage {
        NEGOTIATION {
            @Override
            FailureKind failAgainst(Fixture fixture, String counterPartyAddress) {
                var negotiation = negotiation(ContractNegotiationStates.REQUESTING, counterPartyAddress, new ProtocolMessages());

                fixture.negotiationProcessors().processRequesting(negotiation).join();

                assertThat(negotiation.getState()).isEqualTo(ContractNegotiationStates.TERMINATED.code());
                return FailureClassifier.classify(negotiation);
            }
        },
        TRANSFER {
            @Override
            FailureKind failAgainst(Fixture fixture, String counterPartyAddress) {
                var transfer = requestingTransfer(counterPartyAddress);

                fixture.transferProcessors().processRequesting(transfer).join();

                assertThat(transfer.getState()).isEqualTo(TransferProcessStates.TERMINATED.code());
                return FailureClassifier.classify(transfer);
            }
        };

        abstract FailureKind failAgainst(Fixture fixture, String counterPartyAddress);
    }

    private static final class Fixture {
        private final NegotiationProcessorsImpl negotiationProcessors;
        private final TransferProcessorsImpl transferProcessors;

        private Fixture(Duration readTimeout, Result<TokenRepresentation> credentials, AudienceResolver audienceResolver,
                        boolean messagesRegistered) {
            var dispatcher = dispatcher(readTimeout, credentials, audienceResolver, messagesRegistered);
            var monitor = mock(Monitor.class);
            var clock = Clock.systemUTC();
            var retryFactory = new EntityRetryProcessFactory(monitor, clock, new EntityRetryProcessConfiguration(0, () -> () -> 0L));
            var webhookResolver = mock(ProtocolWebhookResolver.class);
            ProtocolWebhook webhook = () -> "http://localhost:1/callback";
            when(webhookResolver.getWebhook(anyString(), anyString())).thenReturn(webhook);
            var registry = CriterionOperatorRegistryImpl.ofDefaults();

            negotiationProcessors = new NegotiationProcessorsImpl(monitor, webhookResolver, new ContractNegotiationObservableImpl(),
                    new InMemoryContractNegotiationStore(clock, registry), mock(ParticipantIdentityResolver.class), clock,
                    dispatcher, new EntityRetryProcessConfiguration(0, () -> () -> 0L));

            var policyArchive = mock(PolicyArchive.class);
            when(policyArchive.findPolicyForContract(anyString()))
                    .thenReturn(Policy.Builder.newInstance().assigner("provider-1").assignee("consumer-1").build());
            when(policyArchive.getAgreementIdForContract(anyString())).thenReturn("agreement-1");
            var dataAddressStore = mock(DataAddressStore.class);
            when(dataAddressStore.resolve(any())).thenReturn(StoreResult.notFound("no data address"));
            when(dataAddressStore.remove(any())).thenReturn(StoreResult.success());
            transferProcessors = new TransferProcessorsImpl(policyArchive, retryFactory, mock(DataFlowController.class), dataAddressStore,
                    new TransferProcessObservableImpl(), new InMemoryTransferProcessStore(clock, registry), monitor,
                    mock(DataAddressResolver.class), webhookResolver, dispatcher);
        }

        static Fixture standard() {
            return new Fixture(Duration.ofSeconds(5), validCredentials(), _ -> Result.success("provider-1"), true);
        }

        NegotiationProcessorsImpl negotiationProcessors() {
            return negotiationProcessors;
        }

        TransferProcessorsImpl transferProcessors() {
            return transferProcessors;
        }

        private static DspHttpRemoteMessageDispatcherImpl dispatcher(Duration readTimeout, Result<TokenRepresentation> credentials,
                                                                      AudienceResolver audienceResolver, boolean messagesRegistered) {
            var okHttpClient = StubbedNetwork.httpClientBuilder().readTimeout(readTimeout).connectTimeout(Duration.ofSeconds(2)).build();
            var retryPolicy = RetryPolicy.<Response>builder().withMaxRetries(0).build();
            var httpClient = new EdcHttpClientImpl(okHttpClient, retryPolicy, mock(Monitor.class));
            var identityService = mock(IdentityService.class);
            when(identityService.obtainClientCredentials(anyString(), any())).thenReturn(credentials);
            var tokenDecorator = mock(TokenDecorator.class);
            when(tokenDecorator.decorate(any())).thenAnswer(invocation -> invocation.getArgument(0));
            var dispatcher = new DspHttpRemoteMessageDispatcherImpl(httpClient, identityService, tokenDecorator,
                    mock(PolicyEngine.class), audienceResolver);
            if (messagesRegistered) {
                dispatcher.registerMessage(ContractRequestMessage.class,
                        message -> request(message.getCounterPartyAddress()), (_, _) -> null);
                dispatcher.registerMessage(ContractAgreementVerificationMessage.class,
                        message -> request(message.getCounterPartyAddress()), (_, _) -> null);
                dispatcher.registerMessage(TransferRequestMessage.class,
                        message -> request(message.getCounterPartyAddress()), (_, _) -> null);
            }
            return dispatcher;
        }

        private static Request request(String counterPartyAddress) {
            return new Request.Builder().url(counterPartyAddress + "/message").get().build();
        }
    }
}

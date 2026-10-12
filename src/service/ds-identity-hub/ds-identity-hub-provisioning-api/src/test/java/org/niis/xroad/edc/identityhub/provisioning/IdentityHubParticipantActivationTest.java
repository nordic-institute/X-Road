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
package org.niis.xroad.edc.identityhub.provisioning;

import io.grpc.stub.StreamObserver;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.identityhub.did.defaults.InMemoryDidResourceStore;
import org.eclipse.edc.identityhub.participantcontext.ParticipantContextCoordinatorExtension;
import org.eclipse.edc.identityhub.participantcontext.ParticipantContextExtension;
import org.eclipse.edc.identityhub.spi.did.DidDocumentService;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.identityhub.spi.keypair.KeyPairService;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.StsAccountProvisioner;
import org.eclipse.edc.identityhub.spi.participantcontext.events.ParticipantContextObservable;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participantcontext.defaults.store.InMemoryParticipantContextStore;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.store.ParticipantContextStore;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContextState;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.runtime.core.event.EventRouterImpl;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextResp;

import java.time.Clock;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the provisioning gRPC service against the upstream participant-context service and its event
 * coordinator, wired by the upstream extensions over in-memory stores.
 */
@ExtendWith(DependencyInjectionExtension.class)
class IdentityHubParticipantActivationTest {

    private static final String PARTICIPANT_CONTEXT_ID = "member-1";
    private static final String DID = "did:web:ih.example.test:member-1";

    private final DidDocumentService didDocumentService = mock();
    private final KeyPairService keyPairService = mock();

    private IdentityHubProvisioningGrpcService service;
    private IdentityHubParticipantContextService participantContextService;

    @BeforeEach
    void setUp(ServiceExtensionContext context, ObjectFactory factory) {
        var monitor = mock(Monitor.class);
        var clock = Clock.systemUTC();
        var criterionOperatorRegistry = CriterionOperatorRegistryImpl.ofDefaults();
        var vault = mock(Vault.class);
        when(vault.storeSecret(anyString(), anyString(), anyString())).thenReturn(Result.success());
        var stsAccountProvisioner = mock(StsAccountProvisioner.class);
        when(stsAccountProvisioner.create(any())).thenReturn(ServiceResult.success(null));
        var configService = mock(ParticipantContextConfigService.class);
        when(configService.save(any())).thenReturn(ServiceResult.success());
        when(didDocumentService.store(any(), anyString())).thenReturn(ServiceResult.success());
        when(keyPairService.addKeyPair(anyString(), any(), anyBoolean())).thenReturn(ServiceResult.success());

        context.registerService(ParticipantContextStore.class, new InMemoryParticipantContextStore(criterionOperatorRegistry));
        context.registerService(DidResourceStore.class, new InMemoryDidResourceStore(criterionOperatorRegistry));
        context.registerService(EventRouter.class, new EventRouterImpl(monitor, Executors.newSingleThreadExecutor(), clock));
        context.registerService(TransactionContext.class, new NoopTransactionContext());
        context.registerService(Telemetry.class, new Telemetry());
        context.registerService(Clock.class, clock);
        context.registerService(Vault.class, vault);
        context.registerService(StsAccountProvisioner.class, stsAccountProvisioner);
        context.registerService(ParticipantContextConfigService.class, configService);
        context.registerService(DidDocumentService.class, didDocumentService);
        context.registerService(KeyPairService.class, keyPairService);

        var participantContextExtension = factory.constructInstance(ParticipantContextExtension.class);
        participantContextService = participantContextExtension.createParticipantService();
        context.registerService(IdentityHubParticipantContextService.class, participantContextService);
        context.registerService(ParticipantContextObservable.class, participantContextExtension.participantContextObservable());
        factory.constructInstance(ParticipantContextCoordinatorExtension.class).initialize(context);

        service = new IdentityHubProvisioningGrpcService(participantContextService, mock(CredentialRequestManager.class),
                mock(ParticipantCredentialRecordsPurger.class), mock(DidResolverRegistry.class), new RpcResponseHandler(), monitor);
    }

    @Test
    @SuppressWarnings("unchecked")
    void participantContextProvisionedThroughGrpcBecomesActive() {
        StreamObserver<CreateParticipantContextResp> observer = mock(StreamObserver.class);

        service.createParticipantContext(createRequest(), observer);

        verify(observer).onCompleted();
        var stored = participantContextService.getParticipantContext(PARTICIPANT_CONTEXT_ID);
        assertThat(stored.succeeded()).isTrue();
        assertThat(stored.getContent().getStateAsEnum()).isEqualTo(ParticipantContextState.ACTIVATED);
        assertThat(stored.getContent().getDid()).isEqualTo(DID);
        verify(didDocumentService).store(any(), eq(PARTICIPANT_CONTEXT_ID));
        verify(keyPairService).addKeyPair(eq(PARTICIPANT_CONTEXT_ID), any(), eq(true));
    }

    private static CreateParticipantContextReq createRequest() {
        return CreateParticipantContextReq.newBuilder()
                .setParticipantContextId(PARTICIPANT_CONTEXT_ID)
                .setDid(DID)
                .setMemberId("DEV/COM/222")
                .setCredentialServiceUrl("https://ih.example.test/credentials")
                .setKeyId(DID + "#key-1")
                .setPrivateKeyAlias("member-1-key")
                .build();
    }
}

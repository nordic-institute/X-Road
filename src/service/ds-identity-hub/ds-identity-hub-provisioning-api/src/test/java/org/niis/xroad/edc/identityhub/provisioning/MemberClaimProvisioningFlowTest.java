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

import ee.ria.xroad.common.identifier.ClientId;

import io.grpc.stub.StreamObserver;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.encryption.EncryptionAlgorithmRegistryImpl;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.identityhub.did.defaults.InMemoryDidResourceStore;
import org.eclipse.edc.identityhub.participantcontext.ParticipantContextCoordinatorExtension;
import org.eclipse.edc.identityhub.participantcontext.ParticipantContextExtension;
import org.eclipse.edc.identityhub.spi.authentication.ParticipantSecureTokenService;
import org.eclipse.edc.identityhub.spi.did.DidDocumentService;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.identityhub.spi.keypair.KeyPairService;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.StsAccountProvisioner;
import org.eclipse.edc.identityhub.spi.participantcontext.events.ParticipantContextObservable;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participantcontext.config.defaults.store.InMemoryParticipantContextConfigStore;
import org.eclipse.edc.participantcontext.config.service.ParticipantContextConfigServiceImpl;
import org.eclipse.edc.participantcontext.config.validation.ParticipantContextConfigValidatorRegistryImpl;
import org.eclipse.edc.participantcontext.defaults.store.InMemoryParticipantContextStore;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.store.ParticipantContextStore;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.runtime.core.event.EventRouterImpl;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.iam.TokenRepresentation;
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
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.ds.identityhub.claim.MemberClaimSigner;
import org.niis.xroad.ds.identityhub.claim.XRoadClaimAwareSecureTokenService;
import org.niis.xroad.ds.identityhub.memberid.ParticipantMemberIdConfig;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.DeleteParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.DeleteParticipantContextResp;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provisions members through the gRPC service over the upstream participant-context service and a real
 * participant configuration service, then requests a credential-request token and checks that the member
 * claim is signed for the provisioned member.
 */
@ExtendWith(DependencyInjectionExtension.class)
class MemberClaimProvisioningFlowTest {

    private static final String PARTICIPANT_CONTEXT_ID = "member-1";
    private static final String DID = "did:web:ih.example.test:member-1";
    private static final String ISSUER_DID = "did:web:issuer.example.test";
    private static final String MEMBER_ID = "DEV/COM/222";
    private static final String OTHER_MEMBER_ID = "DEV/COM/333";

    private final MemberClaimSigner signer = mock();
    private final ParticipantSecureTokenService delegate = mock();
    private final StreamObserver<CreateParticipantContextResp> createObserver = mock();
    private final StreamObserver<DeleteParticipantContextResp> deleteObserver = mock();

    private IdentityHubProvisioningGrpcService service;
    private ParticipantContextConfigService configService;
    private ParticipantSecureTokenService tokenService;

    @BeforeEach
    void setUp(ServiceExtensionContext context, ObjectFactory factory) {
        var monitor = mock(Monitor.class);
        var clock = Clock.systemUTC();
        var criterionOperatorRegistry = CriterionOperatorRegistryImpl.ofDefaults();
        var vault = mock(Vault.class);
        when(vault.storeSecret(anyString(), anyString(), anyString())).thenReturn(Result.success());
        when(vault.deleteSecret(anyString(), anyString())).thenReturn(Result.success());
        var stsAccountProvisioner = mock(StsAccountProvisioner.class);
        when(stsAccountProvisioner.create(any())).thenReturn(ServiceResult.success(null));
        var didDocumentService = mock(DidDocumentService.class);
        when(didDocumentService.store(any(), anyString())).thenReturn(ServiceResult.success());
        when(didDocumentService.unpublish(anyString())).thenReturn(ServiceResult.success());
        when(didDocumentService.deleteById(anyString())).thenReturn(ServiceResult.success());
        var keyPairService = mock(KeyPairService.class);
        when(keyPairService.addKeyPair(anyString(), any(), anyBoolean())).thenReturn(ServiceResult.success());
        when(keyPairService.query(any())).thenReturn(ServiceResult.success(List.of()));
        var transactionContext = new NoopTransactionContext();
        configService = new ParticipantContextConfigServiceImpl(new EncryptionAlgorithmRegistryImpl(), null,
                new InMemoryParticipantContextConfigStore(), transactionContext, clock,
                new ParticipantContextConfigValidatorRegistryImpl(false));

        context.registerService(ParticipantContextStore.class, new InMemoryParticipantContextStore(criterionOperatorRegistry));
        context.registerService(DidResourceStore.class, new InMemoryDidResourceStore(criterionOperatorRegistry));
        context.registerService(EventRouter.class, new EventRouterImpl(monitor, Executors.newSingleThreadExecutor(), clock));
        context.registerService(TransactionContext.class, transactionContext);
        context.registerService(Telemetry.class, new Telemetry());
        context.registerService(Clock.class, clock);
        context.registerService(Vault.class, vault);
        context.registerService(StsAccountProvisioner.class, stsAccountProvisioner);
        context.registerService(ParticipantContextConfigService.class, configService);
        context.registerService(DidDocumentService.class, didDocumentService);
        context.registerService(KeyPairService.class, keyPairService);

        var participantContextExtension = factory.constructInstance(ParticipantContextExtension.class);
        var participantContextService = participantContextExtension.createParticipantService();
        context.registerService(IdentityHubParticipantContextService.class, participantContextService);
        context.registerService(ParticipantContextObservable.class, participantContextExtension.participantContextObservable());
        factory.constructInstance(ParticipantContextCoordinatorExtension.class).initialize(context);

        var recordsPurger = mock(ParticipantCredentialRecordsPurger.class);
        when(recordsPurger.purge(anyString())).thenReturn(new ParticipantCredentialRecordsPurger.PurgeCounts(0, 0));
        var memberIdConfig = new ParticipantMemberIdConfig(configService);
        service = new IdentityHubProvisioningGrpcService(participantContextService, memberIdConfig,
                mock(CredentialRequestManager.class),
                recordsPurger, mock(DidResolverRegistry.class), new RpcResponseHandler(), monitor);

        when(delegate.createToken(anyString(), anyMap(), any()))
                .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("outer-jwt").build()));
        when(signer.sign(any(), anyString(), anyString())).thenReturn(Result.success("signed-jws"));
        tokenService = new XRoadClaimAwareSecureTokenService(delegate, participantContextService, memberIdConfig, signer, monitor);
    }

    @Test
    void provisionedMemberIsFoundByTheTokenService() {
        service.createParticipantContext(createRequest(MEMBER_ID, false), createObserver);
        requestToken();
        assertThat(signedMember()).isEqualTo(MEMBER_ID);
    }

    @Test
    void reanchoredMemberIsFoundByTheTokenService() {
        service.createParticipantContext(createRequest(MEMBER_ID, false), createObserver);
        requestToken();
        assertThat(signedMember()).isEqualTo(MEMBER_ID);
        clearInvocations(signer);

        service.createParticipantContext(createRequest(OTHER_MEMBER_ID, true), createObserver);

        requestToken();
        assertThat(signedMember()).isEqualTo(OTHER_MEMBER_ID);
    }

    @Test
    void deletedMemberLeavesNoMemberIdConfigEntry() {
        service.createParticipantContext(createRequest(MEMBER_ID, false), createObserver);
        service.deleteParticipantContext(DeleteParticipantContextReq.newBuilder()
                .setParticipantContextId(PARTICIPANT_CONTEXT_ID).build(), deleteObserver);
        verify(deleteObserver).onCompleted();
        var config = configService.get(PARTICIPANT_CONTEXT_ID);
        assertThat(config.failed() || !config.getContent().getPrivateEntries().containsKey("xroadMemberId")).isTrue();
    }

    private void requestToken() {
        tokenService.createToken(PARTICIPANT_CONTEXT_ID, new HashMap<>(Map.of("aud", ISSUER_DID)), null);
    }

    private String signedMember() {
        var memberCaptor = ArgumentCaptor.forClass(ClientId.class);
        verify(signer).sign(memberCaptor.capture(), anyString(), anyString());
        var member = memberCaptor.getValue();
        return String.join("/", member.getXRoadInstance(), member.getMemberClass(), member.getMemberCode());
    }

    private static CreateParticipantContextReq createRequest(String memberId, boolean reanchor) {
        return CreateParticipantContextReq.newBuilder()
                .setParticipantContextId(PARTICIPANT_CONTEXT_ID)
                .setDid(DID)
                .setMemberId(memberId)
                .setReanchorMemberIdOnConflict(reanchor)
                .setCredentialServiceUrl("https://ih.example.test/credentials")
                .setKeyId(DID + "#key-1")
                .setPrivateKeyAlias("member-1-key")
                .build();
    }
}

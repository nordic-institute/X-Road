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

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.encryption.EncryptionAlgorithmRegistryImpl;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
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
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participantcontext.config.defaults.store.InMemoryParticipantContextConfigStore;
import org.eclipse.edc.participantcontext.config.service.ParticipantContextConfigServiceImpl;
import org.eclipse.edc.participantcontext.config.validation.ParticipantContextConfigValidatorRegistryImpl;
import org.eclipse.edc.participantcontext.defaults.store.InMemoryParticipantContextStore;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.store.ParticipantContextStore;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.runtime.core.event.EventRouterImpl;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.ds.identityhub.memberid.ParticipantMemberIdConfig;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.rpc.error.XrdRuntimeExceptionProto;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;

/**
 * Provisions a member through the gRPC service over the upstream participant-context service while one
 * provisioning step fails. The upstream service rolls the creation back and reports the failure; the tests check
 * that the failure reaches the caller as an error, that nothing of the member remains, and that a retry succeeds.
 * <p>
 * The in-memory stores take no part in transactions, so {@link RollbackTransactionContext} undoes the writes that
 * a database transaction would undo. Key material and vault entries live outside the transaction and are only
 * removed by the upstream cleanup, as in production.
 */
@ExtendWith(DependencyInjectionExtension.class)
class ProvisioningRollbackTest {

    private static final String PARTICIPANT_CONTEXT_ID = "member-1";
    private static final String DID = "did:web:ih.example.test:member-1";
    private static final String KEY_ID = DID + "#key-1";
    private static final String MEMBER_ID = "DEV/COM/222";

    private final AtomicReference<FailingStep> failingStep = new AtomicReference<>();
    private final AtomicReference<ConflictingStep> conflictingStep = new AtomicReference<>();
    private final AtomicInteger vaultWrites = new AtomicInteger();
    private final Map<String, String> vaultEntries = new HashMap<>();
    private final Map<String, DidDocument> didDocuments = new HashMap<>();
    private final Map<String, KeyDescriptor> keyMaterial = new HashMap<>();
    private final StreamObserver<CreateParticipantContextResp> observer = mock();
    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor();

    private IdentityHubProvisioningGrpcService service;
    private ParticipantContextStore participantContextStore;
    private ParticipantMemberIdConfig memberIdConfig;

    @BeforeEach
    void setUp(ServiceExtensionContext context, ObjectFactory factory) {
        var monitor = mock(Monitor.class);
        var clock = Clock.systemUTC();
        var criterionOperatorRegistry = CriterionOperatorRegistryImpl.ofDefaults();
        var transactionContext = new RollbackTransactionContext();
        participantContextStore = new FaultInjectingParticipantContextStore(
                new InMemoryParticipantContextStore(criterionOperatorRegistry), transactionContext, failingStep);

        var vault = mock(Vault.class);
        when(vault.storeSecret(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            if (conflictingStep.get() == ConflictingStep.VAULT) {
                return Result.failure(ConflictingStep.VAULT.detail);
            }
            vaultWrites.incrementAndGet();
            vaultEntries.put(invocation.getArgument(0) + "/" + invocation.getArgument(1), invocation.getArgument(2));
            return Result.success();
        });
        when(vault.deleteSecret(anyString(), anyString())).thenAnswer(invocation -> {
            vaultEntries.remove(invocation.getArgument(0) + "/" + invocation.getArgument(1));
            return Result.success();
        });
        var stsAccountProvisioner = mock(StsAccountProvisioner.class);
        when(stsAccountProvisioner.create(any())).thenReturn(ServiceResult.success(null));

        var didDocumentService = mock(DidDocumentService.class);
        when(didDocumentService.store(any(), anyString())).thenAnswer(invocation -> {
            if (failingStep.get() == FailingStep.DID_DOCUMENT) {
                return ServiceResult.unexpected(FailingStep.DID_DOCUMENT.detail);
            }
            DidDocument document = invocation.getArgument(0);
            didDocuments.put(document.getId(), document);
            transactionContext.onRollback(() -> didDocuments.remove(document.getId()));
            return ServiceResult.success();
        });
        var keyPairService = mock(KeyPairService.class);
        when(keyPairService.addKeyPair(anyString(), any(), anyBoolean())).thenAnswer(invocation -> {
            if (failingStep.get() == FailingStep.KEY_PAIR) {
                return ServiceResult.unexpected(FailingStep.KEY_PAIR.detail);
            }
            if (conflictingStep.get() == ConflictingStep.KEY_PAIR) {
                return ServiceResult.conflict(ConflictingStep.KEY_PAIR.detail);
            }
            KeyDescriptor key = invocation.getArgument(1);
            keyMaterial.put(key.getKeyId(), key);
            return ServiceResult.success();
        });

        doAnswer(invocation -> keyMaterial.remove(((KeyDescriptor) invocation.getArgument(1)).getKeyId()))
                .when(keyPairService).discardKeyMaterial(anyString(), any());

        var configService = new ParticipantContextConfigServiceImpl(new EncryptionAlgorithmRegistryImpl(), null,
                new InMemoryParticipantContextConfigStore(), transactionContext, clock,
                new ParticipantContextConfigValidatorRegistryImpl(false));

        context.registerService(ParticipantContextStore.class, participantContextStore);
        context.registerService(DidResourceStore.class, new InMemoryDidResourceStore(criterionOperatorRegistry));
        context.registerService(EventRouter.class, new EventRouterImpl(monitor, eventExecutor, clock));
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

        memberIdConfig = new ParticipantMemberIdConfig(configService);
        service = new IdentityHubProvisioningGrpcService(participantContextService, memberIdConfig,
                mock(CredentialRequestManager.class), mock(ParticipantCredentialRecordsPurger.class),
                mock(DidResolverRegistry.class), new RpcResponseHandler(), monitor);
    }

    @AfterEach
    void tearDown() {
        eventExecutor.shutdownNow();
    }

    @ParameterizedTest
    @EnumSource(FailingStep.class)
    void failingStepIsReportedAsProvisioningFailure(FailingStep step) throws InvalidProtocolBufferException {
        failingStep.set(step);

        service.createParticipantContext(createRequest(), observer);

        var error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        verify(observer, never()).onCompleted();
        assertProvisioningFailure(error.getValue(), step.detail);
    }

    @ParameterizedTest
    @EnumSource(FailingStep.class)
    void failingStepLeavesNothingOfTheMember(FailingStep step) {
        failingStep.set(step);

        service.createParticipantContext(createRequest(), observer);

        verify(observer).onError(any(StatusRuntimeException.class));
        assertThat(vaultWrites).as("API key was stored before the failure").hasPositiveValue();
        assertThat(participantContextStore.findById(PARTICIPANT_CONTEXT_ID).succeeded()).isFalse();
        assertThat(participantContextStore.query(QuerySpec.max()).getContent()).isEmpty();
        assertThat(didDocuments).isEmpty();
        assertThat(keyMaterial).isEmpty();
        assertThat(vaultEntries).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(FailingStep.class)
    void retryAfterFailingStepSucceeds(FailingStep step) {
        failingStep.set(step);
        service.createParticipantContext(createRequest(), observer);
        verify(observer).onError(any(StatusRuntimeException.class));
        failingStep.set(null);
        StreamObserver<CreateParticipantContextResp> retryObserver = mock();

        service.createParticipantContext(createRequest(), retryObserver);

        verify(retryObserver, never()).onError(any());
        verify(retryObserver).onCompleted();
        assertThat(participantContextStore.findById(PARTICIPANT_CONTEXT_ID).succeeded()).isTrue();
        assertThat(didDocuments).containsOnlyKeys(DID);
        assertThat(keyMaterial).containsOnlyKeys(KEY_ID);
        assertThat(memberIdConfig.find(PARTICIPANT_CONTEXT_ID)).contains(MEMBER_ID);
    }

    @ParameterizedTest
    @EnumSource(ConflictingStep.class)
    void conflictingStepIsReportedAsErrorNotAsExistingMember(ConflictingStep step) throws InvalidProtocolBufferException {
        conflictingStep.set(step);

        service.createParticipantContext(createRequest(), observer);

        var error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        verify(observer, never()).onNext(any());
        verify(observer, never()).onCompleted();
        assertProvisioningFailure(error.getValue(), step.detail);
    }

    @ParameterizedTest
    @EnumSource(ConflictingStep.class)
    void conflictingStepLeavesNothingOfTheMember(ConflictingStep step) {
        conflictingStep.set(step);

        service.createParticipantContext(createRequest(), observer);

        verify(observer).onError(any(StatusRuntimeException.class));
        assertThat(participantContextStore.findById(PARTICIPANT_CONTEXT_ID).succeeded()).isFalse();
        assertThat(participantContextStore.query(QuerySpec.max()).getContent()).isEmpty();
        assertThat(didDocuments).isEmpty();
        assertThat(keyMaterial).isEmpty();
        assertThat(vaultEntries).isEmpty();
        assertThat(memberIdConfig.find(PARTICIPANT_CONTEXT_ID)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ConflictingStep.class)
    void retryAfterConflictingStepSucceeds(ConflictingStep step) {
        conflictingStep.set(step);
        service.createParticipantContext(createRequest(), observer);
        verify(observer).onError(any(StatusRuntimeException.class));
        conflictingStep.set(null);
        StreamObserver<CreateParticipantContextResp> retryObserver = mock();

        service.createParticipantContext(createRequest(), retryObserver);

        verify(retryObserver, never()).onError(any());
        verify(retryObserver).onCompleted();
        assertThat(participantContextStore.findById(PARTICIPANT_CONTEXT_ID).succeeded()).isTrue();
        assertThat(didDocuments).containsOnlyKeys(DID);
        assertThat(keyMaterial).containsOnlyKeys(KEY_ID);
        assertThat(memberIdConfig.find(PARTICIPANT_CONTEXT_ID)).contains(MEMBER_ID);
    }

    private static void assertProvisioningFailure(Throwable error, String detail) throws InvalidProtocolBufferException {
        var status = StatusProto.fromThrowable(error);
        assertThat(status).isNotNull();
        assertThat(status.getMessage()).contains(detail);
        var details = status.getDetailsList().stream()
                .filter(packed -> packed.is(XrdRuntimeExceptionProto.class))
                .findFirst()
                .orElseThrow()
                .unpack(XrdRuntimeExceptionProto.class);
        assertThat(details.getErrorCode()).isEqualTo(ErrorOrigin.DATASPACE.toPrefix() + DSP_PARTICIPANT_CONTEXT_FAILED.code());
        assertThat(details.getErrorMetadataList()).contains(PARTICIPANT_CONTEXT_ID);
    }

    private static CreateParticipantContextReq createRequest() {
        return CreateParticipantContextReq.newBuilder()
                .setParticipantContextId(PARTICIPANT_CONTEXT_ID)
                .setDid(DID)
                .setMemberId(MEMBER_ID)
                .setCredentialServiceUrl("https://ih.example.test/credentials")
                .setKeyId(KEY_ID)
                .setPrivateKeyAlias("member-1-key")
                .build();
    }

    enum FailingStep {
        DID_DOCUMENT("DID document store unavailable"),
        KEY_PAIR("key pair generation unavailable"),
        ACTIVATION("participant context activation unavailable");

        private final String detail;

        FailingStep(String detail) {
            this.detail = detail;
        }
    }

    enum ConflictingStep {
        KEY_PAIR("key pair alias already in use"),
        VAULT("vault unavailable");

        private final String detail;

        ConflictingStep(String detail) {
            this.detail = detail;
        }
    }

    static class RollbackTransactionContext implements TransactionContext {
        private final List<Runnable> undo = new ArrayList<>();
        private int depth;

        @Override
        public void execute(TransactionBlock block) {
            execute((ResultTransactionBlock<Void>) () -> {
                block.execute();
                return null;
            });
        }

        @Override
        public <T> T execute(ResultTransactionBlock<T> block) {
            depth++;
            try {
                return block.execute();
            } catch (RuntimeException e) {
                if (depth == 1) {
                    undo.reversed().forEach(Runnable::run);
                }
                throw e;
            } finally {
                depth--;
                if (depth == 0) {
                    undo.clear();
                }
            }
        }

        @Override
        public void registerSynchronization(TransactionSynchronization sync) {
            throw new UnsupportedOperationException();
        }

        void onRollback(Runnable action) {
            undo.add(action);
        }
    }

    static class FaultInjectingParticipantContextStore implements ParticipantContextStore {
        private final ParticipantContextStore delegate;
        private final RollbackTransactionContext transactionContext;
        private final AtomicReference<FailingStep> failingStep;

        FaultInjectingParticipantContextStore(ParticipantContextStore delegate, RollbackTransactionContext transactionContext,
                                              AtomicReference<FailingStep> failingStep) {
            this.delegate = delegate;
            this.transactionContext = transactionContext;
            this.failingStep = failingStep;
        }

        @Override
        public StoreResult<Collection<ParticipantContext>> query(QuerySpec querySpec) {
            return delegate.query(querySpec);
        }

        @Override
        public StoreResult<Void> create(ParticipantContext participantContext) {
            var result = delegate.create(participantContext);
            if (result.succeeded()) {
                transactionContext.onRollback(() -> delegate.deleteById(participantContext.getId()));
            }
            return result;
        }

        @Override
        public StoreResult<Void> update(ParticipantContext participantContext) {
            if (failingStep.get() == FailingStep.ACTIVATION) {
                return StoreResult.generalError(FailingStep.ACTIVATION.detail);
            }
            return delegate.update(participantContext);
        }

        @Override
        public StoreResult<Void> deleteById(String id) {
            return delegate.deleteById(id);
        }

        @Override
        public StoreResult<ParticipantContext> findById(String participantContextId) {
            return delegate.findById(participantContextId);
        }
    }
}

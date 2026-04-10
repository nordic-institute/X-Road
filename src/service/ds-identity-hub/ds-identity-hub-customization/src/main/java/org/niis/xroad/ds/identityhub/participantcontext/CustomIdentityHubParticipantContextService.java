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

package org.niis.xroad.ds.identityhub.participantcontext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.identityhub.participantcontext.ApiTokenGenerator;
import org.eclipse.edc.identityhub.participantcontext.IdentityHubParticipantContextServiceImpl;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.identityhub.spi.participantcontext.StsAccountProvisioner;
import org.eclipse.edc.identityhub.spi.participantcontext.events.ParticipantContextObservable;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.store.ParticipantContextStore;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContextState;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.eclipse.edc.spi.result.ServiceResult.conflict;
import static org.eclipse.edc.spi.result.ServiceResult.fromFailure;
import static org.eclipse.edc.spi.result.ServiceResult.notFound;
import static org.eclipse.edc.spi.result.ServiceResult.success;

/**
 * Overrides the default {@link IdentityHubParticipantContextServiceImpl} to honour the
 * {@code active} flag in {@link ParticipantManifest}.
 *
 * <p>EDC 0.16.0 always creates participant contexts in {@code CREATED} state regardless
 * of {@code manifest.isActive()}. This subclass overrides
 * {@link #createParticipantContext(ParticipantManifest)} to immediately activate the
 * context when {@code active = true}, which allows DID document publishing to proceed.
 */
class CustomIdentityHubParticipantContextService extends IdentityHubParticipantContextServiceImpl {

    private static final String API_KEY_ALIAS_SUFFIX = "apikey";
    private final ParticipantContextStore participantContextStore;
    private final DidResourceStore didResourceStore;
    private final Vault vault;
    private final TransactionContext transactionContext;
    private final ApiTokenGenerator tokenGenerator;
    private final ParticipantContextObservable observable;
    private final StsAccountProvisioner stsAccountProvisioner;
    private final ParticipantContextConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    CustomIdentityHubParticipantContextService(ParticipantContextStore participantContextStore,
                                               DidResourceStore didResourceStore,
                                               Vault vault,
                                               TransactionContext transactionContext,
                                               ParticipantContextObservable observable,
                                               StsAccountProvisioner stsAccountProvisioner,
                                               ParticipantContextConfigService configService) {
        super(participantContextStore, didResourceStore, vault, transactionContext, observable, stsAccountProvisioner, configService);
        this.participantContextStore = participantContextStore;
        this.didResourceStore = didResourceStore;
        this.vault = vault;
        this.transactionContext = transactionContext;
        this.observable = observable;
        this.stsAccountProvisioner = stsAccountProvisioner;
        this.configService = configService;
        this.tokenGenerator = new ApiTokenGenerator();
    }

    @Override
    public ServiceResult<CreateParticipantContextResponse> createParticipantContext(ParticipantManifest manifest) {
        return transactionContext.execute(() -> {
            if (didResourceStore.findById(manifest.getDid()) != null) {
                return ServiceResult.conflict("Another participant with the same DID '%s' already exists.".formatted(manifest.getDid()));
            }
            var context = convert(manifest);

            return createParticipantContext(context)
                    .compose(this::createTokenAndStoreInVault)
                    .compose((Function<String, ServiceResult<CreateParticipantContextResponse>>) apiKey -> stsAccountProvisioner.create(manifest)
                            .map(accountInfo -> {
                                if (accountInfo == null) {
                                    return new CreateParticipantContextResponse(apiKey, null, null);
                                } else {
                                    return new CreateParticipantContextResponse(apiKey, accountInfo.clientId(), accountInfo.clientSecret());
                                }
                            }))
                    .onSuccess(apiToken -> observable.invokeForEach(l -> l.created(context, manifest)));
        });
    }

    @Override
    public ServiceResult<IdentityHubParticipantContext> getParticipantContext(String participantContextId) {
        return transactionContext.execute(() -> ServiceResult.from(participantContextStore.findById(participantContextId))
                .map(this::convert));
    }

    @Override
    public ServiceResult<Void> deleteParticipantContext(String participantContextId) {
        return transactionContext.execute(() -> {
            var participantContext = findByIdInternal(participantContextId);
            if (participantContext == null) {
                return ServiceResult.notFound("A ParticipantContext with ID '%s' does not exist.");
            }
            return updateParticipant(participantContextId, IdentityHubParticipantContext::deactivate)
                    .compose(v -> {
                        observable.invokeForEach(l -> l.deleting(participantContext));
                        var res = participantContextStore.deleteById(participantContextId);
                        vault.deleteSecret(participantContext.getParticipantContextId(), participantContext.getApiTokenAlias());
                        if (res.failed()) {
                            return fromFailure(res);
                        }

                        observable.invokeForEach(l -> l.deleted(participantContext));
                        return ServiceResult.success();
                    });
        });
    }

    @Override
    public ServiceResult<String> regenerateApiToken(String participantContextId) {
        return transactionContext.execute(() -> {
            var participantContext = getParticipantContext(participantContextId);
            if (participantContext.failed()) {
                return participantContext.map(pc -> null);
            }
            return createTokenAndStoreInVault(participantContext.getContent());
        });
    }

    @Override
    public ServiceResult<Void> updateParticipant(String participantContextId, Consumer<IdentityHubParticipantContext> modificationFunction) {
        return transactionContext.execute(() -> {
            var participant = findByIdInternal(participantContextId);
            if (participant == null) {
                return notFound("ParticipantContext with ID '%s' not found.".formatted(participantContextId));
            }
            modificationFunction.accept(participant);
            var res = participantContextStore.update(participant)
                    .onSuccess(u -> observable.invokeForEach(l -> l.updated(participant)));
            return res.succeeded() ? success() : fromFailure(res);
        });
    }

    @Override
    public ServiceResult<Collection<IdentityHubParticipantContext>> query(QuerySpec querySpec) {
        return transactionContext.execute(() -> ServiceResult.from(participantContextStore.query(querySpec))
                .map(participantContexts -> participantContexts.stream().map(this::convert)
                        .collect(Collectors.toList())));
    }

    private ServiceResult<String> createTokenAndStoreInVault(IdentityHubParticipantContext participantContext) {
        var alias = participantContext.getApiTokenAlias();
        var newToken = tokenGenerator.generate(participantContext.getParticipantContextId());
        return vault.storeSecret(participantContext.getParticipantContextId(), alias, newToken)
                .map(unused -> success(newToken))
                .orElse(f -> conflict("Could not store new API token: %s.".formatted(f.getFailureDetail())));
    }

    private ServiceResult<IdentityHubParticipantContext> createParticipantContext(IdentityHubParticipantContext context) {
        var result = participantContextStore.create(context);

        var config = context.getProperties().entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> {
                    if (e.getValue() instanceof String v) {
                        return v;
                    }
                    try {
                        return objectMapper.writeValueAsString(e.getValue());
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                }));

        var cfg = ParticipantContextConfiguration.Builder.newInstance()
                .participantContextId(context.getParticipantContextId())
                .privateEntries(config)
                .build();
        var configResult = configService.save(cfg);
        return configResult.compose(u -> ServiceResult.from(result).map(it -> context));
    }

    private IdentityHubParticipantContext findByIdInternal(String participantContextId) {
        var resultStream = participantContextStore.findById(participantContextId)
                .map(this::convert);
        return resultStream.orElse(f -> null);
    }

    private IdentityHubParticipantContext convert(ParticipantManifest manifest) {
        var apiKeyAlias = ofNullable(manifest.getApiKeyAlias()).orElse("%s-%s".formatted(manifest.getParticipantContextId(), API_KEY_ALIAS_SUFFIX));
        var context = IdentityHubParticipantContext.Builder.newInstance()
                .participantContextId(manifest.getParticipantContextId())
                .roles(manifest.getRoles())
                .did(manifest.getDid())
                .apiTokenAlias(apiKeyAlias)
                // This whole class is just to edit this single line
                .state(manifest.isActive() ? ParticipantContextState.ACTIVATED : ParticipantContextState.CREATED)
                .properties(manifest.getAdditionalProperties());
        if (manifest.getRoles().isEmpty()) {
            context.roles(List.of("participant"));
        }

        return context.build();
    }

    private IdentityHubParticipantContext convert(ParticipantContext participantContext) {
        return IdentityHubParticipantContext.Builder.newInstance()
                .participantContextId(participantContext.getParticipantContextId())
                .did(participantContext.getIdentity())
                .state(participantContext.getStateAsEnum())
                .createdAt(participantContext.getCreatedAt())
                .lastModified(participantContext.getLastModified())
                .properties(participantContext.getProperties())
                .build();
    }
}

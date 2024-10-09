/*
 *  Copyright (c) 2023 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.identityhub.did;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKParameterNames;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.spi.document.VerificationMethod;
import org.eclipse.edc.identithub.spi.did.DidDocumentPublisherRegistry;
import org.eclipse.edc.identithub.spi.did.DidDocumentService;
import org.eclipse.edc.identithub.spi.did.model.DidResource;
import org.eclipse.edc.identithub.spi.did.model.DidState;
import org.eclipse.edc.identithub.spi.did.store.DidResourceStore;
import org.eclipse.edc.identityhub.spi.keypair.events.KeyPairAdded;
import org.eclipse.edc.identityhub.spi.keypair.events.KeyPairRevoked;
import org.eclipse.edc.identityhub.spi.participantcontext.events.ParticipantContextCreated;
import org.eclipse.edc.identityhub.spi.participantcontext.events.ParticipantContextDeleted;
import org.eclipse.edc.identityhub.spi.participantcontext.events.ParticipantContextUpdated;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantResource;
import org.eclipse.edc.keys.spi.KeyParserRegistry;
import org.eclipse.edc.security.token.jwt.CryptoConverter;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.AbstractResult;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.security.KeyPair;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.eclipse.edc.spi.result.ServiceResult.success;

/**
 * This is an aggregate service to manage CRUD operations of {@link DidDocument}s as well as handle their
 * publishing and un-publishing. All methods are executed transactionally.
 */
public class DidDocumentServiceImpl implements DidDocumentService, EventSubscriber {

    private final TransactionContext transactionContext;
    private final DidResourceStore didResourceStore;
    private final DidDocumentPublisherRegistry registry;
    private final Monitor monitor;
    private final KeyParserRegistry keyParserRegistry;

    public DidDocumentServiceImpl(TransactionContext transactionContext, DidResourceStore didResourceStore, DidDocumentPublisherRegistry registry, Monitor monitor, KeyParserRegistry keyParserRegistry) {
        this.transactionContext = transactionContext;
        this.didResourceStore = didResourceStore;
        this.registry = registry;
        this.monitor = monitor;
        this.keyParserRegistry = keyParserRegistry;
    }

    @Override
    public ServiceResult<Void> store(DidDocument document, String participantId) {
        return transactionContext.execute(() -> {
            var res = DidResource.Builder.newInstance()
                    .document(document)
                    .did(document.getId())
                    .participantId(participantId)
                    .build();
            var result = didResourceStore.save(res);
            return result.succeeded() ?
                    success() :
                    ServiceResult.fromFailure(result);
        });
    }

    @Override
    public ServiceResult<Void> deleteById(String did) {
        return transactionContext.execute(() -> {
            var existing = didResourceStore.findById(did);
            if (existing == null) {
                return ServiceResult.notFound(notFoundMessage(did));
            }
            if (existing.getState() == DidState.PUBLISHED.code()) {
                return ServiceResult.conflict("Cannot delete DID '%s' because it is already published. Un-publish first!".formatted(did));
            }
            var res = didResourceStore.deleteById(did);
            return res.succeeded() ?
                    success() :
                    ServiceResult.fromFailure(res);
        });
    }

    @Override
    public ServiceResult<Void> publish(String did) {
        return transactionContext.execute(() -> {
            var existingDoc = didResourceStore.findById(did);
            if (existingDoc == null) {
                return ServiceResult.notFound(notFoundMessage(did));
            }
            var publisher = registry.getPublisher(did);
            if (publisher == null) {
                return ServiceResult.badRequest(noPublisherFoundMessage(did));
            }
            var publishResult = publisher.publish(did);
            return publishResult.succeeded() ?
                    success() :
                    ServiceResult.badRequest(publishResult.getFailureDetail());

        });
    }

    @Override
    public ServiceResult<Void> unpublish(String did) {
        return transactionContext.execute(() -> {
            var existingDoc = didResourceStore.findById(did);
            if (existingDoc == null) {
                return ServiceResult.notFound(notFoundMessage(did));
            }
            var publisher = registry.getPublisher(did);
            if (publisher == null) {
                return ServiceResult.badRequest(noPublisherFoundMessage(did));
            }
            var publishResult = publisher.unpublish(did);
            return publishResult.succeeded() ?
                    success() :
                    ServiceResult.badRequest(publishResult.getFailureDetail());

        });
    }


    @Override
    public ServiceResult<Collection<DidDocument>> queryDocuments(QuerySpec query) {
        return transactionContext.execute(() -> {
            var res = didResourceStore.query(query);
            return success(res.stream().map(DidResource::getDocument).toList());
        });
    }

    @Override
    public DidResource findById(String did) {
        return transactionContext.execute(() -> didResourceStore.findById(did));
    }

    @Override
    public ServiceResult<Void> addService(String did, Service service) {
        return transactionContext.execute(() -> {
            var didResource = didResourceStore.findById(did);
            if (didResource == null) {
                return ServiceResult.notFound("DID '%s' not found.".formatted(did));
            }
            var services = didResource.getDocument().getService();
            if (services.stream().anyMatch(s -> s.getId().equals(service.getId()))) {
                return ServiceResult.conflict("DID '%s' already contains a service endpoint with ID '%s'.".formatted(did, service.getId()));
            }
            services.add(service);
            var updateResult = didResourceStore.update(didResource);
            return updateResult.succeeded() ?
                    success() :
                    ServiceResult.fromFailure(updateResult);

        });
    }

    @Override
    public ServiceResult<Void> replaceService(String did, Service service) {
        return transactionContext.execute(() -> {
            var didResource = didResourceStore.findById(did);
            if (didResource == null) {
                return ServiceResult.notFound("DID '%s' not found.".formatted(did));
            }
            var services = didResource.getDocument().getService();
            if (services.stream().noneMatch(s -> s.getId().equals(service.getId()))) {
                return ServiceResult.badRequest("DID '%s' does not contain a service endpoint with ID '%s'.".formatted(did, service.getId()));
            }
            services.add(service);
            var updateResult = didResourceStore.update(didResource);
            return updateResult.succeeded() ?
                    success() :
                    ServiceResult.fromFailure(updateResult);
        });
    }

    @Override
    public ServiceResult<Void> removeService(String did, String serviceId) {
        return transactionContext.execute(() -> {
            var didResource = didResourceStore.findById(did);
            if (didResource == null) {
                return ServiceResult.notFound("DID '%s' not found.".formatted(did));
            }
            var services = didResource.getDocument().getService();
            var hasRemoved = services.removeIf(s -> s.getId().equals(serviceId));
            if (!hasRemoved) {
                return ServiceResult.badRequest("DID '%s' does not contain a service endpoint with ID '%s'.".formatted(did, serviceId));
            }
            var updateResult = didResourceStore.update(didResource);
            return updateResult.succeeded() ?
                    success() :
                    ServiceResult.fromFailure(updateResult);

        });
    }

    @Override
    public <E extends Event> void on(EventEnvelope<E> eventEnvelope) {
        var payload = eventEnvelope.getPayload();
        if (payload instanceof ParticipantContextUpdated event) {
            updated(event);
        } else if (payload instanceof ParticipantContextDeleted event) {
            deleted(event);
        } else if (payload instanceof KeyPairAdded event) {
            keypairAdded(event);
        } else if (payload instanceof KeyPairRevoked event) {
            keypairRevoked(event);
        } else {
            monitor.warning("Received event with unexpected payload type: %s".formatted(payload.getClass()));
        }
    }

    private void keypairRevoked(KeyPairRevoked event) {
        var didResources = findByParticipantId(event.getParticipantId());
        var keyId = event.getKeyId();

        var errors = didResources.stream()
                .peek(didResource -> didResource.getDocument().getVerificationMethod().removeIf(vm -> vm.getId().equals(keyId)))
                .map(didResourceStore::update)
                .filter(StoreResult::failed)
                .map(AbstractResult::getFailureDetail)
                .collect(Collectors.joining(","));

        if (!errors.isEmpty()) {
            monitor.warning("Updating DID documents after revoking a KeyPair failed: %s".formatted(errors));
        }
    }

    private void keypairAdded(KeyPairAdded event) {
        var didResources = findByParticipantId(event.getParticipantId());
        if (didResources.isEmpty()) {
            monitor.warning("No DidResources were found for participant '%s'. No updated will be performed.".formatted(event.getParticipantId()));
            return;
        }
        var serialized = event.getPublicKeySerialized();
        var publicKey = keyParserRegistry.parse(serialized);

        if (publicKey.failed()) {
            monitor.warning("Error adding KeyPair '%s' to DID Document of participant '%s': %s".formatted(event.getKeyPairResourceId(), event.getParticipantId(), publicKey.getFailureDetail()));
            return;
        }

        var wipJwk = CryptoConverter.createJwk(new KeyPair((PublicKey) publicKey.getContent(), null));

        try {
            // append x5u to jwk if it can be parsed from serialized
            var x5u = JWK.parse(serialized).getX509CertURL();
            var jwkJson = wipJwk.toJSONObject();
            jwkJson.put(JWKParameterNames.X_509_CERT_URL, x5u.toString());
            jwkJson.put(JWKParameterNames.ALGORITHM, "RS256"); // gaia-x compliance unfortunately requires this to be present
            wipJwk = JWK.parse(jwkJson);
        } catch (ParseException e) {
            monitor.warning("Error appending x5u to JWK", e);
        }

        JWK jwk = wipJwk;
        var errors = didResources.stream()
                .peek(dd -> dd.getDocument().getVerificationMethod().add(VerificationMethod.Builder.newInstance()
                        .id(event.getKeyId())
                        .publicKeyJwk(jwk.toJSONObject())
                        .controller(dd.getDocument().getId())
                        .type(event.getType())
                        .build()))
                .map(didResourceStore::update)
                .filter(StoreResult::failed)
                .map(AbstractResult::getFailureDetail)
                .collect(Collectors.joining(","));

        if (!errors.isEmpty()) {
            monitor.warning("Updating DID documents after adding a KeyPair failed: %s".formatted(errors));
        }

    }

    private void updated(ParticipantContextUpdated event) {
        var newState = event.getNewState();
        var forParticipant = findByParticipantId(event.getParticipantId());
        var errors = forParticipant
                .stream()
                .map(resource -> switch (newState) {
                    case ACTIVATED -> publish(resource.getDid());
                    case DEACTIVATED -> unpublish(resource.getDid());
                    default -> ServiceResult.success();
                })
                .filter(AbstractResult::failed)
                .map(AbstractResult::getFailureDetail)
                .collect(Collectors.joining(", "));

        if (!errors.isEmpty()) {
            monitor.warning("Updating DID documents after updating a ParticipantContext failed: %s".formatted(errors));
        }
    }

    private void deleted(ParticipantContextDeleted event) {
        var participantId = event.getParticipantId();
        //unpublish and delete all DIDs associated with that participant
        var errors = findByParticipantId(participantId)
                .stream()
                .map(didResource -> unpublish(didResource.getDid()).compose(u -> deleteById(didResource.getDid())))
                .filter(AbstractResult::failed)
                .map(AbstractResult::getFailureDetail)
                .collect(Collectors.joining(", "));

        if (!errors.isEmpty()) {
            monitor.warning("Unpublishing/deleting DID documents after deleting a ParticipantContext failed: %s".formatted(errors));
        }
    }

    private Collection<DidResource> findByParticipantId(String participantId) {
        return didResourceStore.query(ParticipantResource.queryByParticipantId(participantId).build());
    }


    private void created(ParticipantContextCreated event) {
        var manifest = event.getManifest();
        var doc = DidDocument.Builder.newInstance()
                .id(manifest.getDid())
                .service(manifest.getServiceEndpoints().stream().toList())
                // updating and adding a verification method happens as a result of the KeyPairAddedEvent
                .build();
        store(doc, manifest.getParticipantId())
                .compose(u -> manifest.isActive() ? publish(doc.getId()) : success())
                .onFailure(f -> monitor.warning("Creating a DID document after creating a ParticipantContext creation failed: %s".formatted(f.getFailureDetail())));
    }
}

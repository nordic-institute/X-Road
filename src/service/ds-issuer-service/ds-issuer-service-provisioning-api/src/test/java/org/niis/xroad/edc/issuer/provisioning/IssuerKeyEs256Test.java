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
package org.niis.xroad.edc.issuer.provisioning;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.iam.did.resolution.DidPublicKeyResolverImpl;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.identityhub.did.DidDocumentServiceImpl;
import org.eclipse.edc.identityhub.keypairs.KeyPairGenerator;
import org.eclipse.edc.identityhub.spi.did.DidDocumentPublisher;
import org.eclipse.edc.identityhub.spi.did.DidDocumentPublisherRegistry;
import org.eclipse.edc.identityhub.spi.did.model.DidResource;
import org.eclipse.edc.identityhub.spi.did.model.DidState;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.identityhub.spi.keypair.events.KeyPairActivated;
import org.eclipse.edc.identityhub.spi.keypair.model.KeyPairResource;
import org.eclipse.edc.issuerservice.issuance.generator.JwtCredentialGenerator;
import org.eclipse.edc.keys.KeyParserRegistryImpl;
import org.eclipse.edc.keys.spi.KeyParser;
import org.eclipse.edc.participantcontext.spi.store.ParticipantContextStore;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContextState;
import org.eclipse.edc.security.token.jwt.CryptoConverter;
import org.eclipse.edc.security.token.jwt.DefaultJwsSignerProvider;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.eclipse.edc.token.JwtGenerationService;
import org.eclipse.edc.token.TokenValidationServiceImpl;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.Key;
import java.security.KeyPair;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssuerKeyEs256Test {
    private static final String PARTICIPANT_CONTEXT_ID = "issuer";
    private static final String ISSUER_DID = "did:web:issuer.example.com";
    private static final String KEY_ID = ISSUER_DID + "#key-1";
    private static final String PRIVATE_KEY_ALIAS = "issuer-key-alias";
    private static final String HOLDER_DID = "did:web:holder.example.com";

    private final DidResourceStore didResourceStore = mock();
    private final DidDocumentPublisherRegistry publisherRegistry = mock();
    private final DidDocumentPublisher publisher = mock();
    private final ParticipantContextStore participantContextStore = mock();
    private final DidResolverRegistry didResolverRegistry = mock();

    private DidDocument didDocument;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() {
        keyPair = KeyPairGenerator.generateKeyPair(Map.of("algorithm", "EC", "curve", "secp256r1")).getContent();
        didDocument = DidDocument.Builder.newInstance().id(ISSUER_DID).build();

        var didResource = DidResource.Builder.newInstance()
                .did(ISSUER_DID)
                .state(DidState.GENERATED)
                .document(didDocument)
                .build();
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.queryForUpdate(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.update(any())).thenReturn(StoreResult.success());
        when(didResourceStore.findById(ISSUER_DID)).thenReturn(didResource);
        when(publisherRegistry.getPublisher(startsWith("did:web:"))).thenReturn(publisher);
        when(publisher.publish(ISSUER_DID)).thenReturn(Result.success());
        when(participantContextStore.findById(any())).thenReturn(StoreResult.success(ParticipantContext.Builder.newInstance()
                .participantContextId(PARTICIPANT_CONTEXT_ID)
                .identity(ISSUER_DID)
                .state(ParticipantContextState.ACTIVATED)
                .build()));
        when(didResolverRegistry.resolve(ISSUER_DID)).thenReturn(Result.success(didDocument));
    }

    @Test
    void didDocumentPublishesIssuerKeyAsEcP256JsonWebKey() {
        activateIssuerKey();

        assertThat(didDocument.getVerificationMethod()).singleElement().satisfies(method -> {
            assertThat(method.getId()).isEqualTo(KEY_ID);
            assertThat(method.getType()).isEqualTo("JsonWebKey2020");
            assertThat(method.getPublicKeyJwk())
                    .containsEntry("kty", "EC")
                    .containsEntry("crv", "P-256")
                    .doesNotContainKey("d");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"MembershipCredential", "BitstringStatusListCredential"})
    void signedCredentialIsEs256AndVerifiesWithKeyFromDidDocument(String credentialType) throws Exception {
        activateIssuerKey();

        var jwt = signCredential(credentialType);

        var signedJwt = SignedJWT.parse(jwt);
        assertThat(signedJwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
        assertThat(signedJwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
        var verification = new TokenValidationServiceImpl().validate(
                TokenRepresentation.Builder.newInstance().token(jwt).build(), didPublicKeyResolver(), List.of());
        assertThat(verification.succeeded()).as("verification failure: %s", verification.getFailureDetail()).isTrue();
    }

    private void activateIssuerKey() {
        var privateJwk = CryptoConverter.createJwk(keyPair, KEY_ID);
        var keyPairResource = KeyPairResource.Builder.newPresentationSigning().id(UUID.randomUUID().toString()).build();
        var didService = new DidDocumentServiceImpl(new NoopTransactionContext(), didResourceStore, publisherRegistry,
                participantContextStore, mock(), keyParserRegistry(), new Telemetry());

        didService.on(EventEnvelope.Builder.newInstance()
                .at(System.currentTimeMillis())
                .id(UUID.randomUUID().toString())
                .payload(KeyPairActivated.Builder.newInstance()
                        .keyId(KEY_ID)
                        .keyPairResource(keyPairResource)
                        .participantContextId(PARTICIPANT_CONTEXT_ID)
                        .publicKey(privateJwk.toPublicJWK().toJSONString(), "JsonWebKey2020")
                        .build())
                .build());
    }

    private String signCredential(String credentialType) {
        var signerProvider = new DefaultJwsSignerProvider((_, _) -> Result.success(keyPair.getPrivate()));
        var generator = new JwtCredentialGenerator(new JwtGenerationService(signerProvider), Clock.systemUTC());
        var credential = VerifiableCredential.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .type("VerifiableCredential")
                .type(credentialType)
                .issuer(new Issuer(ISSUER_DID))
                .issuanceDate(Instant.now())
                .expirationDate(Instant.now().plusSeconds(3600))
                .credentialSubject(CredentialSubject.Builder.newInstance().id(HOLDER_DID).claim("status", "active").build())
                .build();

        var result = generator.signCredential(PARTICIPANT_CONTEXT_ID, credential, PRIVATE_KEY_ALIAS, KEY_ID);
        assertThat(result.succeeded()).as("signing failure: %s", result.getFailureDetail()).isTrue();
        return result.getContent();
    }

    private DidPublicKeyResolverImpl didPublicKeyResolver() {
        return new DidPublicKeyResolverImpl(keyParserRegistry(), didResolverRegistry);
    }
    private static KeyParserRegistryImpl keyParserRegistry() {
        var registry = new KeyParserRegistryImpl();
        registry.register(new KeyParser() {
            @Override
            public boolean canHandle(String encoded) {
                return encoded.startsWith("{");
            }
            @Override
            public Result<Key> parse(String encoded) {
                try {
                    return Result.success(JWK.parse(encoded).toECKey().toPublicKey());
                } catch (ParseException | JOSEException e) {
                    return Result.failure(e.getMessage());
                }
            }
        });
        return registry;
    }
}

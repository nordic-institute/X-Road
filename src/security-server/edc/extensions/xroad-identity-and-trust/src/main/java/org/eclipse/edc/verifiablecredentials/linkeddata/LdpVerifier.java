/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.verifiablecredentials.linkeddata;

import com.apicatalog.jsonld.json.JsonUtils;
import com.apicatalog.jsonld.lang.Keywords;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.DocumentError.ErrorType;
import com.apicatalog.ld.Term;
import com.apicatalog.ld.node.LdNode;
import com.apicatalog.ld.node.LdType;
import com.apicatalog.ld.signature.VerificationError;
import com.apicatalog.ld.signature.VerificationMethod;
import com.apicatalog.ld.signature.key.VerificationKey;
import com.apicatalog.vc.Presentation;
import com.apicatalog.vc.VcVocab;
import com.apicatalog.vc.method.resolver.HttpMethodResolver;
import com.apicatalog.vc.method.resolver.MethodResolver;
import com.apicatalog.vc.proof.EmbeddedProof;
import com.apicatalog.vc.proof.Proof;
import com.apicatalog.vc.suite.SignatureSuite;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import org.eclipse.edc.iam.identitytrust.spi.verification.CredentialVerifier;
import org.eclipse.edc.iam.identitytrust.spi.verification.SignatureSuiteRegistry;
import org.eclipse.edc.iam.identitytrust.spi.verification.VerifierContext;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.util.uri.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

public final class LdpVerifier implements CredentialVerifier {

    private JsonLd jsonLd;
    private ObjectMapper jsonLdMapper;
    private SignatureSuiteRegistry suiteRegistry;
    private Map<String, Object> params;
    private Collection<MethodResolver> methodResolvers = new ArrayList<>(List.of(new HttpMethodResolver()));
    private DocumentLoader documentLoader;
    private URI base;

    private LdpVerifier() {
    }

    @Override
    public boolean canHandle(String rawInput) {
        try (var parser = jsonLdMapper.createParser(rawInput)) {
            parser.nextToken();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Verifies a VerifiableCredential (VC) or a VerifiablePresentation (VP), represented as JSON-LD-string.
     *
     * @param rawInput        The raw JSON-LD string. This must be either a VerifiablePresentation or a VerifiableCredential.
     *                        Note that the JSON-LD will be expanded before processing.
     * @param verifierContext The {@link VerifierContext} to which nested credentials are delegated
     */
    @Override
    public Result<Void> verify(String rawInput, VerifierContext verifierContext) {
        JsonObject jo;
        try {
            jo = jsonLdMapper.readValue(rawInput, JsonObject.class);
        } catch (JsonProcessingException e) {
            return failure("Failed to parse JSON: %s".formatted(e.toString()));
        }
        var context = jo.containsKey(Keywords.CONTEXT)
                ? JsonUtils.toJsonArray(jo.get(Keywords.CONTEXT))
                : null;
        var expansion = jsonLd.expand(jo);

        if (documentLoader == null) {
            // default loader
            documentLoader = SchemeRouter.defaultInstance();
        }
        return expansion.compose(expandedDocument -> {
            try {
                return verifyExpanded(expandedDocument, verifierContext, context);
            } catch (DocumentError e) {
                return failure("Could not verify VP-LDP: message: %s, code: %s".formatted(e.getMessage(), e.getCode()));
            } catch (VerificationError e) {
                return failure("Could not verify VP-LDP: %s | message: %s".formatted(e.getCode(), e.getMessage()));
            }
        });
    }

    public URI getBase() {
        return base;
    }

    private VerificationMethod resolveMethod(URI id, Proof proof, DocumentLoader loader) throws DocumentError {

        if (id == null) {
            throw new DocumentError(ErrorType.Missing, "ProofVerificationId");
        }

        // find the method id resolver
        final Optional<MethodResolver> resolver = methodResolvers.stream()
                .filter(r -> r.isAccepted(id))
                .findFirst();

        // try to resolve the method
        if (resolver.isPresent()) {
            return resolver.get().resolve(id, loader, proof);
        }

        throw new DocumentError(ErrorType.Unknown, "ProofVerificationId");
    }

    /**
     * Validates the credential issuer by comparing it with the provided verification method.
     *
     * @param expanded           The expanded JSON-LD object representing the credential.
     * @param verificationMethod The verification method to compare with the issuer.
     * @return A {@link Result} indicating success or failure.
     */
    private Result<Void> validateCredentialIssuer(JsonObject expanded, VerificationMethod verificationMethod) {
        try {
            var issuerUri = LdNode.of(expanded).node(VcVocab.ISSUER);
            if (!issuerUri.exists()) {
                return failure("Document must contain an 'issuer' property.");
            }
            if (!UriUtils.equalsIgnoreFragment(issuerUri.id(), verificationMethod.id())) {
                return failure("Issuer and proof.verificationMethod mismatch: %s <> %s".formatted(issuerUri, verificationMethod.id()));
            }
        } catch (DocumentError e) {
            return failure("Error getting issuer: %s".formatted(e.getMessage()));
        }
        return success();
    }

    private Result<Void> verifyExpanded(JsonObject expanded, VerifierContext context, JsonStructure ldContext)
            throws VerificationError, DocumentError {

        if (isCredential(expanded)) {
            // data integrity validation
            return verifyProofs(expanded, ldContext);

        } else if (isPresentation(expanded)) {
            // verify presentation proofs
            var presentationValidation = verifyProofs(expanded, ldContext);
            if (!presentationValidation.succeeded()) {
                return presentationValidation.mapTo();
            }

            // verify embedded credentials

            // verifiableCredentials
            var credentials = new ArrayList<JsonObject>();
            for (var credential : Presentation.getCredentials(expanded)) {

                if (JsonUtils.isNotObject(credential)) {
                    return failure("Presentation contained an invalid 'verifiableCredential' object!");
                }
                credentials.add(credential.asJsonObject());
            }

            return credentials.stream()
                    .map(expCred -> context.verify(expCred.toString()))
                    .reduce(Result::merge)
                    .orElse(success()); // "no credentials" is still valid according to https://www.w3.org/TR/vc-data-model/#presentations-0

        } else {
            return failure("%s: %s".formatted(ErrorType.Unknown, Term.TYPE));
        }
    }

    private Result<Void> verifyProofs(JsonObject expanded, JsonStructure context) throws VerificationError, DocumentError {

        // get proofs - throws an exception if there is no proof, never null nor an
        // empty collection
        var expandedProofs = EmbeddedProof.assertProof(expanded);


        var allProofs = new ArrayList<Proof>(expandedProofs.size());

        // a data before issuance - no proof attached
        var data = EmbeddedProof.removeProofs(expanded);

        // verify attached proofs' signatures
        for (var expandedProof : expandedProofs) {

            if (JsonUtils.isNotObject(expandedProof)) {
                return failure("%s: %s".formatted(ErrorType.Invalid, VcVocab.PROOF));
            }

            var proofTypes = LdType.strings(expandedProof);

            if (proofTypes == null || proofTypes.isEmpty()) {
                return failure("%s: %s, %s".formatted(ErrorType.Missing, VcVocab.PROOF, Term.TYPE));
            }

            var signatureSuite = findSuite(proofTypes, expandedProof);

            if (signatureSuite == null) {
                return failure("No SignatureSuite found for proof type(s) '%s'.".formatted(String.join(",")));
            }

            var proof = signatureSuite.getProof(expandedProof, documentLoader);
            if (proof == null) {
                return failure("The suite [" + signatureSuite + "] returns null as a proof.");
            }

            allProofs.add(proof);
        }

        for (var proof : allProofs) {
            try {
                proof.validate(Map.of());

                var proofValue = proof.signature();

                if (proofValue == null) {
                    return failure("Proof did not contain a valid signature.");
                }

                var verificationMethod = getMethod(proof, documentLoader);
                if (verificationMethod == null) {
                    return failure("Proof did not contain a VerificationMethod.");
                }

                if (!(verificationMethod instanceof VerificationKey)) {
                    return failure("Proof did not contain a valid VerificationMethod, expected VerificationKey, got: %s"
                            .formatted(verificationMethod.getClass()));
                }

                if (isCredential(expanded)) {
                    var failure = validateCredentialIssuer(expanded, verificationMethod);
                    if (failure.failed()) return failure;
                }

                proof.verify(context, data, (VerificationKey) verificationMethod);
            } catch (DocumentError error) {
                return failure("Could not verify VP-LDP: message: %s, code: %s".formatted(error.getMessage(), error.getCode()));
            } catch (VerificationError error) {
                return failure("Verification failed: %s".formatted(error.getMessage()));
            }
        }
        return success();
    }

    private VerificationMethod getMethod(Proof proof, DocumentLoader loader) throws DocumentError {
        final VerificationMethod method = proof.method();

        if (method == null) {
            throw new DocumentError(ErrorType.Missing, "ProofVerificationMethod");
        }

        final URI methodType = method.type();

        if (methodType != null && method instanceof VerificationKey && (((VerificationKey) method).publicKey() != null)) {
            return method;
        }

        return resolveMethod(method.id(), proof, loader);
    }

    private SignatureSuite findSuite(Collection<String> proofTypes, JsonObject expandedProof) {
        return suiteRegistry.getAllSuites().stream()
                .filter(s -> proofTypes.stream().anyMatch(type -> s.isSupported(type, expandedProof)))
                .findFirst().orElse(null);
    }

    private boolean isCredential(JsonObject expanded) {
        return LdNode.isTypeOf(VcVocab.CREDENTIAL_TYPE.uri(), expanded);
    }

    private boolean isPresentation(JsonObject expanded) {
        return LdNode.isTypeOf(VcVocab.PRESENTATION_TYPE.uri(), expanded);
    }

    public static final class Builder {
        private final LdpVerifier verifier;

        private Builder() {
            verifier = new LdpVerifier();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder signatureSuites(SignatureSuiteRegistry registry) {
            this.verifier.suiteRegistry = registry;
            return this;
        }

        public Builder params(Map<String, Object> params) {
            this.verifier.params = params;
            return this;
        }

        public Builder param(String key, Object object) {
            this.verifier.params.put(key, object);
            return this;
        }

        public Builder methodResolvers(Collection<MethodResolver> resolvers) {
            this.verifier.methodResolvers = resolvers;
            return this;
        }

        public Builder methodResolver(MethodResolver resolver) {
            this.verifier.methodResolvers.add(resolver);
            return this;
        }

        public Builder objectMapper(ObjectMapper mapper) {
            this.verifier.jsonLdMapper = mapper;
            return this;
        }

        public Builder jsonLd(JsonLd jsonLd) {
            this.verifier.jsonLd = jsonLd;
            return this;
        }

        /**
         * If set, this overrides the input document's IRI.
         *
         * @return the processor instance
         */
        public Builder base(URI base) {
            this.verifier.base = base;
            return this;
        }

        public Builder loader(DocumentLoader loader) {
            this.verifier.documentLoader = loader;
            return this;
        }

        public LdpVerifier build() {
            Objects.requireNonNull(this.verifier.jsonLd, "Must have a JsonLD service!");
            Objects.requireNonNull(this.verifier.jsonLdMapper, "Must have an ObjectMapper!");
            Objects.requireNonNull(this.verifier.suiteRegistry, "Must have a Signature registry!");
            return this.verifier;
        }
    }
}

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

import com.apicatalog.jsonld.InvalidJsonLdValue;
import com.apicatalog.jsonld.JsonLdReader;
import com.apicatalog.jsonld.json.JsonUtils;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.DocumentError.ErrorType;
import com.apicatalog.ld.schema.LdProperty;
import com.apicatalog.ld.schema.LdTerm;
import com.apicatalog.ld.signature.LinkedDataSignature;
import com.apicatalog.ld.signature.SignatureSuite;
import com.apicatalog.ld.signature.VerificationError;
import com.apicatalog.ld.signature.VerificationError.Code;
import com.apicatalog.ld.signature.key.VerificationKey;
import com.apicatalog.ld.signature.method.HttpMethodResolver;
import com.apicatalog.ld.signature.method.MethodResolver;
import com.apicatalog.ld.signature.method.VerificationMethod;
import com.apicatalog.ld.signature.proof.EmbeddedProof;
import com.apicatalog.vc.VcTag;
import com.apicatalog.vc.VcVocab;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.iam.identitytrust.spi.verification.CredentialVerifier;
import org.eclipse.edc.iam.identitytrust.spi.verification.SignatureSuiteRegistry;
import org.eclipse.edc.iam.identitytrust.spi.verification.VerifierContext;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
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

public class LdpVerifier implements CredentialVerifier {

    private JsonLd jsonLd;
    private ObjectMapper jsonLdMapper;
    private SignatureSuiteRegistry suiteRegistry;
    private Map<String, Object> params;
    private Collection<MethodResolver> methodResolvers = new ArrayList<>(List.of(new HttpMethodResolver()));
    private DocumentLoader loader;
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
        var expansion = jsonLd.expand(jo);

        if (loader == null) {
            // default loader
            loader = SchemeRouter.defaultInstance();
        }
        return expansion.compose(expandedDocument -> {
            try {
                return verifyExpanded(expandedDocument, verifierContext);
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

    /**
     * Validates the credential issuer by comparing it with the provided verification method.
     *
     * @param expanded           The expanded JSON-LD object representing the credential.
     * @param verificationMethod The verification method to compare with the issuer.
     * @return A {@link Result} indicating success or failure.
     */
    private Result<Void> validateCredentialIssuer(JsonObject expanded, VerificationMethod verificationMethod) {
        try {
            var issuerUri = JsonLdReader.getId(expanded, VcVocab.ISSUER.uri());
            if (issuerUri.isEmpty()) {
                return failure("Document must contain an 'issuer' property.");
            }
            if (!UriUtils.equalsIgnoreFragment(issuerUri.get(), verificationMethod.id())) {
                return failure("Issuer and proof.verificationMethod mismatch: %s <> %s".formatted(issuerUri.get(), verificationMethod.id()));
            }
        } catch (InvalidJsonLdValue e) {
            return failure("Error getting issuer: %s".formatted(e.getMessage()));
        }
        return success();
    }

    /**
     * Extracts the first graph from a JSON-LD document, if it exists. When multiple VCs are present in a VP, they are
     * expanded to a {@code @graph} object.
     *
     * @param document The JSON-LD document to extract the graph from.
     * @return The first graph from the JSON-LD document, or the original document if no graph exists.
     */
    private JsonValue extractGraph(JsonValue document) {
        if (document.getValueType() == JsonValue.ValueType.OBJECT) {
            if (document.asJsonObject().get(JsonLdKeywords.GRAPH) != null) {
                return document.asJsonObject().getJsonArray(JsonLdKeywords.GRAPH).get(0);
            }
        }
        return document;
    }

    private Result<Void> verifyExpanded(JsonObject expanded, VerifierContext context) throws VerificationError, DocumentError {


        if (isCredential(expanded)) {
            // data integrity validation
            return verifyGaiaXProofs(expanded);

        } else if (isPresentation(expanded)) {
            // verify presentation proofs
            verifyProofs(expanded);

            // verify embedded credentials

            // verifiableCredentials
            var credentials = new ArrayList<JsonObject>();
            for (var credential : JsonLdReader.getObjects(expanded, VcVocab.VERIFIABLE_CREDENTIALS.uri())) {

                if (JsonUtils.isNotObject(credential)) {
                    return failure("Presentation contained an invalid 'verifiableCredential' object!");
                }
                credentials.add(credential.asJsonObject());
            }

            return credentials.stream()
                    .map(this::extractGraph)
                    .map(expCred -> context.verify(expCred.toString()))
                    .reduce(Result::merge)
                    .orElse(success()); // "no credentials" is still valid according to https://www.w3.org/TR/vc-data-model/#presentations-0

        } else {
            return failure("%s: %s".formatted(ErrorType.Unknown, LdTerm.TYPE));
        }
    }

    private Result<Void> verifyProofs(JsonObject expanded) throws VerificationError, DocumentError {

        // get proofs - throws an exception if there is no proof, never null nor an
        // empty collection
        var proofs = EmbeddedProof.assertProof(expanded);

        // a data before issuance - no proof attached
        var data = EmbeddedProof.removeProof(expanded);

        // verify attached proofs' signatures
        for (var embeddedProof : proofs) {

            if (JsonUtils.isNotObject(embeddedProof)) {
                return failure("%s: %s".formatted(ErrorType.Invalid, VcVocab.PROOF));
            }

            var proofObject = embeddedProof.asJsonObject();

            var proofType = JsonLdReader.getType(proofObject);

            if (proofType == null || proofType.isEmpty()) {
                return failure("%s: %s, %s".formatted(ErrorType.Missing, VcVocab.PROOF, LdTerm.TYPE));
            }

            var signatureSuite = proofType.stream()
                    .map(suiteRegistry::getForId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new VerificationError(Code.UnsupportedCryptoSuite));

            if (signatureSuite.getSchema() == null) {
                return failure("The suite [" + signatureSuite.getId() + "] does not provide proof schema.");
            }

            LdProperty<byte[]> proofValueProperty = signatureSuite.getSchema().tagged(VcTag.ProofValue.name());

            if (proofValueProperty == null) {
                return failure("The proof schema does not define the proof value.");
            }

            var proof = signatureSuite.getSchema().read(proofObject);

            signatureSuite.getSchema().validate(proof, params);

            if (!proof.contains(proofValueProperty.term())) {
                return failure("%s: %s".formatted(ErrorType.Missing, proofValueProperty.term()));
            }

            byte[] proofValue = proof.value(proofValueProperty.term());

            if (proofValue == null || proofValue.length == 0) {
                return failure("%s: %s".formatted(ErrorType.Missing, proofValueProperty.term()));
            }

            LdProperty<VerificationMethod> methodProperty = signatureSuite.getSchema().tagged(VcTag.VerificationMethod.name());

            if (methodProperty == null) {
                return failure("The proof schema does not define a verification method.");
            }

            var verificationMethod = getMethod(methodProperty, proofObject, signatureSuite)
                    .orElseThrow(() -> new DocumentError(ErrorType.Missing, methodProperty.term()));

            if (!(verificationMethod instanceof VerificationKey)) {
                return failure("%s: %s".formatted(ErrorType.Unknown, methodProperty.term()));
            }

            if (isCredential(expanded)) {
                var failure = validateCredentialIssuer(expanded, verificationMethod);
                if (failure.failed()) return failure;
            }

            // remote a proof value
            var unsignedProof = Json.createObjectBuilder(proofObject)
                    .remove(proofValueProperty.term().uri())
                    .build();

            var signature = new LinkedDataSignature(signatureSuite.getCryptoSuite());

            // verify signature
            signature.verify(data, unsignedProof, (VerificationKey) verificationMethod, proofValue);
        }
        // all good
        return success();
    }

    private Result<Void> verifyGaiaXProofs(JsonObject expanded) throws VerificationError, DocumentError {

        // get proofs - throws an exception if there is no proof, never null nor an
        // empty collection
        var proofs = EmbeddedProof.assertProof(expanded);

        // a data before issuance - no proof attached
        var data = EmbeddedProof.removeProof(expanded);

        // verify attached proofs' signatures
        for (var embeddedProof : proofs) {

            if (JsonUtils.isNotObject(embeddedProof)) {
                return failure("%s: %s".formatted(ErrorType.Invalid, VcVocab.PROOF));
            }

            var proofObject = embeddedProof.asJsonObject();

            var proofType = JsonLdReader.getType(proofObject);

            if (proofType == null || proofType.isEmpty()) {
                return failure("%s: %s, %s".formatted(ErrorType.Missing, VcVocab.PROOF, LdTerm.TYPE));
            }

            var signatureSuite = proofType.stream()
                    .map(suiteRegistry::getForId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new VerificationError(Code.UnsupportedCryptoSuite));

            if (signatureSuite.getSchema() == null) {
                return failure("The suite [" + signatureSuite.getId() + "] does not provide proof schema.");
            }

            LdProperty<byte[]> proofValueProperty = signatureSuite.getSchema().tagged(VcTag.ProofValue.name());

            if (proofValueProperty == null) {
                return failure("The proof schema does not define the proof value.");
            }

            var proof = signatureSuite.getSchema().read(proofObject);

            signatureSuite.getSchema().validate(proof, params);

            if (!proof.contains(proofValueProperty.term())) {
                return failure("%s: %s".formatted(ErrorType.Missing, proofValueProperty.term()));
            }

            byte[] proofValue = proof.value(proofValueProperty.term());

            if (proofValue == null || proofValue.length == 0) {
                return failure("%s: %s".formatted(ErrorType.Missing, proofValueProperty.term()));
            }

            LdProperty<VerificationMethod> methodProperty = signatureSuite.getSchema().tagged(VcTag.VerificationMethod.name());

            if (methodProperty == null) {
                return failure("The proof schema does not define a verification method.");
            }

            var verificationMethod = getMethod(methodProperty, proofObject, signatureSuite)
                    .orElseThrow(() -> new DocumentError(ErrorType.Missing, methodProperty.term()));

            if (!(verificationMethod instanceof VerificationKey)) {
                return failure("%s: %s".formatted(ErrorType.Unknown, methodProperty.term()));
            }

            if (isCredential(expanded)) {
                var failure = validateCredentialIssuer(expanded, verificationMethod);
                if (failure.failed()) return failure;
            }

            var signature = new LinkedDataGaiaXSignature(signatureSuite.getCryptoSuite());

            // verify signature
            signature.verify(data, (VerificationKey) verificationMethod, proofValue);
        }
        // all good
        return success();
    }

    private Optional<VerificationMethod> getMethod(LdProperty<VerificationMethod> property, JsonObject proofObject, SignatureSuite suite) throws DocumentError {

        var expanded = proofObject.getJsonArray(property.term().uri());

        if (JsonUtils.isNull(expanded) || expanded.isEmpty()) {
            return Optional.empty();
        }

        for (var methodValue : expanded) {

            if (JsonUtils.isNotObject(methodValue)) {
                throw new IllegalStateException(); // should never happen
            }

            var methodObject = methodValue.asJsonObject();

            var types = JsonLdReader.getType(methodObject);

            if (types == null || types.isEmpty()) {
                return resolve(methodObject, suite, property);
            }

            var method = property.read(methodObject);

            if (method instanceof VerificationKey && (((VerificationKey) method).publicKey() != null)) {
                return Optional.of(method);
            }

            return resolve(method.id(), suite, property);
        }

        return Optional.empty();
    }

    private Optional<VerificationMethod> resolve(JsonObject method, SignatureSuite suite, LdProperty<VerificationMethod> property) throws DocumentError {
        try {
            var id = JsonLdReader
                    .getId(method)
                    .orElseThrow(() -> new DocumentError(ErrorType.Missing, property.term()));

            return resolve(id, suite, property);

        } catch (InvalidJsonLdValue e) {
            throw new DocumentError(e, ErrorType.Invalid, property.term());
        }

    }

    private Optional<VerificationMethod> resolve(URI id, SignatureSuite suite, LdProperty<VerificationMethod> property) throws DocumentError {

        if (id == null) {
            throw new DocumentError(ErrorType.Invalid, property.term());
        }

        // find the method id resolver
        var resolver = methodResolvers.stream()
                .filter(r -> r.isAccepted(id))
                .findFirst();

        // try to resolve the method
        if (resolver.isPresent()) {
            return Optional.ofNullable(resolver.get().resolve(id, loader, suite));
        }

        throw new DocumentError(ErrorType.Unknown, property.term());
    }

    private boolean isCredential(JsonObject expanded) {
        return JsonLdReader.isTypeOf(VcVocab.CREDENTIAL_TYPE.uri(), expanded);
    }

    private boolean isPresentation(JsonObject expanded) {
        return JsonLdReader.isTypeOf(VcVocab.PRESENTATION_TYPE.uri(), expanded);
    }

    public static class Builder {
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
            this.verifier.loader = loader;
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

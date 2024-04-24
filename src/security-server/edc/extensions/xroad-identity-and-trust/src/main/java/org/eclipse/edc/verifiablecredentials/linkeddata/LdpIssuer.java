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

import com.apicatalog.jsonld.JsonLdReader;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.LinkedDataSignature;
import com.apicatalog.ld.signature.SigningError;
import com.apicatalog.ld.signature.key.KeyPair;
import com.apicatalog.ld.signature.proof.EmbeddedProof;
import com.apicatalog.ld.signature.proof.ProofOptions;
import com.apicatalog.vc.VcTag;
import com.apicatalog.vc.loader.StaticContextLoader;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.net.URI;
import java.util.Objects;

/**
 * The LdpIssuer class is responsible for signing JSON-LD documents (e.g. VerifiableCredentials, VerifiablePresentations) using Linked Data Proofs (LDP).
 * It provides methods for signing a document with a given key pair and proof options.
 */
public class LdpIssuer {
    // mandatory properties
    private JsonLd jsonLdService;
    private DocumentLoader loader;
    private boolean bundledContexts;
    private URI base;
    private Monitor monitor;

    private LdpIssuer() {
    }

    /**
     * Sign a document using a given key pair and proof options. Effectively, this method adds a {@code proof}-object to the JSON document.
     * The document is expanded internally, using the {@link JsonLd} interface.
     *
     * @param document     The document to sign.
     * @param keyPair      The key pair used for signing.
     * @param proofOptions The proof options.
     * @return A result containing the signed document as a JsonObject, a failure otherwise.
     * @throws NullPointerException If any of the parameters is null.
     */
    public Result<JsonObject> signDocument(JsonObject document, KeyPair keyPair, ProofOptions proofOptions) {
        Objects.requireNonNull(document, "Document must not be null");
        Objects.requireNonNull(document, "KeyPair must not be null");
        Objects.requireNonNull(document, "ProofOptions must not be null");
        if (loader == null) {
            // default loader
            loader = SchemeRouter.defaultInstance();
        }

        if (bundledContexts) {
            loader = new StaticContextLoader(loader);
        }

        return jsonLdService.expand(document)
                .compose(expanded -> signExpanded(expanded, keyPair, proofOptions));

    }

    private Result<JsonObject> signExpanded(JsonObject expanded, KeyPair keyPair, ProofOptions options) {

        var optionalObject = JsonLdReader.findFirstObject(expanded);
        if (optionalObject.isEmpty()) {
            return Result.failure("Error reading document: %s".formatted(DocumentError.ErrorType.Invalid));
        }
        var object = optionalObject.get();

        if (options.getSuite() == null) {
            return Result.failure("Unsupported Crypto Suite: %s".formatted(SigningError.Code.UnsupportedCryptoSuite));
        }

        var data = EmbeddedProof.removeProof(object);

        var ldSignature = new LinkedDataSignature(options.getSuite().getCryptoSuite());

        JsonObject proof;
        try {
            proof = options.getSuite().getSchema().write(options.toUnsignedProof());
        } catch (DocumentError e) {
            monitor.warning("Error writing proof", e);
            return Result.failure("Error writing proof: %s".formatted(e.getMessage()));
        }

        var proofValueProperty = options.getSuite().getSchema().tagged(VcTag.ProofValue.name());

        byte[] signature;
        try {
            signature = ldSignature.sign(data, keyPair, proof);
        } catch (SigningError e) {
            monitor.warning("Error signing data", e);
            return Result.failure("Error signing data: %s".formatted(e.getMessage()));
        }

        JsonValue proofValue;
        try {
            proofValue = proofValueProperty.write(signature);
        } catch (DocumentError e) {
            monitor.warning("Error writing signature value to document", e);
            return Result.failure("Error writing signature to document: %s".formatted(e.getCode()));
        }

        proof = Json.createObjectBuilder(proof)
                .add(proofValueProperty.term().uri(), Json.createArrayBuilder().add(proofValue))
                .build();

        return Result.success(EmbeddedProof.addProof(object, proof));
    }


    public static final class Builder {
        private final LdpIssuer ldpIssuer;

        private Builder() {
            ldpIssuer = new LdpIssuer();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder jsonLd(JsonLd jsonLd) {
            this.ldpIssuer.jsonLdService = jsonLd;
            return this;
        }

        public Builder loader(DocumentLoader loader) {
            this.ldpIssuer.loader = loader;
            return this;
        }

        public Builder bundledContexts(boolean bundledContexts) {
            this.ldpIssuer.bundledContexts = bundledContexts;
            return this;
        }

        public Builder base(URI base) {
            this.ldpIssuer.base = base;
            return this;
        }

        public Builder monitor(Monitor monitor) {
            this.ldpIssuer.monitor = monitor;
            return this;
        }

        public LdpIssuer build() {
            Objects.requireNonNull(ldpIssuer.jsonLdService, "Must have a JsonLd instance");
            Objects.requireNonNull(ldpIssuer.monitor, "Monitor cannot be null");
            return ldpIssuer;
        }
    }
}

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

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.SigningError;
import com.apicatalog.ld.signature.key.KeyPair;
import com.apicatalog.vc.issuer.ProofDraft;
import com.apicatalog.vc.loader.StaticContextLoader;
import com.apicatalog.vc.suite.SignatureSuite;
import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.net.URI;
import java.util.Objects;

/**
 * The LdpIssuer class is responsible for signing JSON-LD documents (e.g. VerifiableCredentials,
 * VerifiablePresentations) using Linked Data Proofs (LDP).
 * It provides methods for signing a document with a given key pair and proof options.
 */
public final class LdpIssuer {
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
     * @param document   The document to sign.
     * @param keyPair    The key pair used for signing.
     * @param proofDraft The proof options.
     * @return A result containing the signed document as a JsonObject, a failure otherwise.
     * @throws NullPointerException If any of the parameters is null.
     */
    public Result<JsonObject> signDocument(SignatureSuite signatureSuite, JsonObject document, KeyPair keyPair, ProofDraft proofDraft) {
        Objects.requireNonNull(signatureSuite, "SignatureSuite must not be null");
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
                .compose(expanded -> signExpanded(signatureSuite, expanded, keyPair, proofDraft));

    }

    private Result<JsonObject> signExpanded(SignatureSuite signatureSuite, JsonObject expanded, KeyPair keyPair, ProofDraft proofDraft) {

        try {
            var signed = signatureSuite.createIssuer(keyPair)
                    .loader(loader)
                    .base(base)
                    .useBundledContexts(bundledContexts)
                    .sign(expanded, proofDraft);
            return Result.success(signed.expanded());
        } catch (SigningError | DocumentError e) {
            monitor.warning("Error signing document", e);
            return Result.failure("Error signing document: " + e.getMessage());
        }

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

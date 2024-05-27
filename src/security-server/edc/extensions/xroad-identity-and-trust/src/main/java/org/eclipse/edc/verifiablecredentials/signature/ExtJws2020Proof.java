/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.verifiablecredentials.signature;

import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.CryptoSuite;
import com.apicatalog.ld.signature.VerificationError;
import com.apicatalog.ld.signature.VerificationMethod;
import com.apicatalog.ld.signature.key.VerificationKey;
import com.apicatalog.vc.method.MethodAdapter;
import com.apicatalog.vc.proof.Proof;
import com.apicatalog.vc.proof.ProofValue;
import com.apicatalog.vc.solid.SolidProofValue;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import org.eclipse.edc.security.signature.jws2020.Jwk2020KeyAdapter;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import static org.eclipse.edc.security.signature.jws2020.Jws2020SignatureSuite.PROOF_VALUE_TERM;

/**
 * Represents the {@code proof} object of a verifiable credential which is backed by a JsonWebKey2020, either embedded or linked.
 */
public class ExtJws2020Proof implements Proof, MethodAdapter {
    private final CryptoSuite cryptoSuite;
    private JsonObject expandedDocument;
    private URI id;
    private Instant created;
    private URI purpose;
    private ProofValue jws;
    private VerificationMethod verificationMethod;
    private Jwk2020KeyAdapter adapter;

    protected ExtJws2020Proof() {
        cryptoSuite = new ExtJws2020CryptoSuite();
    }

    @Override
    public VerificationMethod read(JsonObject jsonObject) throws DocumentError {
        return adapter.read(jsonObject);
    }

    @Override
    public JsonObject write(VerificationMethod verificationMethod) {
        return adapter.write(verificationMethod);
    }

    @Override
    public VerificationMethod method() {
        return verificationMethod;
    }

    @Override
    public ProofValue signature() {
        return jws;
    }

    @Override
    public URI id() {
        return id;
    }

    @Override
    public URI previousProof() {
        return null;
    }

    @Override
    public CryptoSuite cryptoSuite() {
        return cryptoSuite;
    }

    @Override
    public void validate(Map<String, Object> map) throws DocumentError {
        if (created == null) {
            throw new DocumentError(DocumentError.ErrorType.Missing, "Created");
        }
        if (verificationMethod == null) {
            throw new DocumentError(DocumentError.ErrorType.Missing, "VerificationMethod");
        }
        if (purpose == null) {
            throw new DocumentError(DocumentError.ErrorType.Missing, "ProofPurpose");
        }
        if (!purpose.equals(URI.create("https://w3id.org/security#assertionMethod"))) {
            throw new DocumentError(DocumentError.ErrorType.Invalid, "ProofPurpose");
        }
        if (jws == null || ((SolidProofValue) jws).toByteArray().length == 0) {
            throw new DocumentError(DocumentError.ErrorType.Missing, "ProofValue");
        }
    }

    @Override
    public void verify(JsonStructure context, JsonObject data, VerificationKey method) throws VerificationError, DocumentError {
        jws.verify(cryptoSuite, context, data, unsigned(), method.publicKey());
    }

    @Override
    public MethodAdapter methodProcessor() {
        return this;
    }

    private JsonObject unsigned() {
        return Json.createObjectBuilder(expandedDocument).remove(PROOF_VALUE_TERM.uri()).build();
    }

    public static final class Builder {
        private final ExtJws2020Proof instance;

        private Builder() {
            instance = new ExtJws2020Proof();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public ExtJws2020Proof build() {
            Objects.requireNonNull(instance.expandedDocument, "JsonDocument cannot be null");
            Objects.requireNonNull(instance.adapter, "Jwk2020KeyAdapter cannot be null");
            return instance;
        }

        public Builder id(URI id) {
            instance.id = id;
            return this;
        }

        public Builder document(JsonObject expandedDocument) {
            instance.expandedDocument = expandedDocument;
            return this;
        }

        public Builder created(Instant instant) {
            instance.created = instant;
            return this;
        }

        public Builder proofPurpose(URI purpose) {
            instance.purpose = purpose;
            return this;
        }

        public Builder jws(ProofValue jws) {
            instance.jws = jws;
            return this;
        }

        public Builder adapter(Jwk2020KeyAdapter adapter) {
            instance.adapter = adapter;
            return this;
        }

        public Builder verificationMethod(VerificationMethod verificationMethod) {
            instance.verificationMethod = verificationMethod;
            return this;
        }
    }
}

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

package org.eclipse.edc.verifiablecredentials.signature;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.Term;
import com.apicatalog.ld.node.LdNode;
import com.apicatalog.ld.signature.key.KeyPair;
import com.apicatalog.vc.integrity.DataIntegrityVocab;
import com.apicatalog.vc.issuer.Issuer;
import com.apicatalog.vc.proof.Proof;
import com.apicatalog.vc.proof.ProofValue;
import com.apicatalog.vc.suite.SignatureSuite;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import org.eclipse.edc.security.signature.jws2020.Jwk2020KeyAdapter;

import static com.apicatalog.vc.VcVocab.SECURITY_VOCAB;

/**
 * {@link SignatureSuite} that provides cryptographic facilities and a key schema for
 * <a href="https://www.w3.org/community/reports/credentials/CG-FINAL-lds-jws2020-20220721/#test-vectors">Json Web Signature 2020</a>.
 */
public final class GaiaXJws2020SignatureSuite implements SignatureSuite {
    public static final String CONTEXT = "https://w3id.org/security/suites/jws-2020/v1";
    public static final String JWS2020_ID = "JsonWebSignature2020";
    public static final Term PROOF_VALUE_TERM = Term.create("jws", SECURITY_VOCAB);
    public static final String ID = SECURITY_VOCAB + JWS2020_ID;
    public final Jwk2020KeyAdapter methodAdapter;

    /**
     * Creates a new {@link Jws2020SignatureSuite} using an object mapper. That mapper is needed because parts of the schema are plain JSON.
     */
    public GaiaXJws2020SignatureSuite(ObjectMapper mapper) {
        methodAdapter = new Jwk2020KeyAdapter(mapper);
    }

    @Override
    public boolean isSupported(String proofType, JsonObject jsonObject) {
        return ID.equals(proofType)
                || JWS2020_ID.equals(proofType); //non-expanded form
    }

    @Override
    public Proof getProof(JsonObject document, DocumentLoader documentLoader) throws DocumentError {
        if (document == null) {
            throw new IllegalArgumentException("The 'document' parameter must not be null.");
        }

        var node = LdNode.of(document);

        return ExtJws2020Proof.Builder.newInstance()
                .id(node.id())
                .document(document)
                .created(node.scalar(DataIntegrityVocab.CREATED).xsdDateTime())
                .proofPurpose(node.node(DataIntegrityVocab.PURPOSE).id())
                .jws(getProofValue(node.scalar(PROOF_VALUE_TERM).string()))
                .adapter(methodAdapter)
                .verificationMethod(node.node(DataIntegrityVocab.VERIFICATION_METHOD).map(methodAdapter))
                .build();
    }

    @Override
    public Issuer createIssuer(KeyPair keyPair) {
        return new GaiaXJwsIssuer(this, keyPair);
    }

    private ProofValue getProofValue(String proofValue) {
        //This is a main change.
        return proofValue != null ? new GaiaXSolidProofValue(proofValue.getBytes()) : null;
    }
}

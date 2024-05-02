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

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.LinkedDataSignature;
import com.apicatalog.ld.signature.SigningError;
import com.apicatalog.ld.signature.key.KeyPair;
import com.apicatalog.multibase.Multibase;
import com.apicatalog.vc.ModelVersion;
import com.apicatalog.vc.VcVocab;
import com.apicatalog.vc.Verifiable;
import com.apicatalog.vc.issuer.AbstractIssuer;
import com.apicatalog.vc.issuer.ProofDraft;
import com.apicatalog.vc.processor.ExpandedVerifiable;
import com.apicatalog.vc.proof.EmbeddedProof;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.edc.security.signature.jws2020.Jws2020ProofDraft;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * replacement for the {@link com.apicatalog.vc.solid.SolidIssuer}, which would add a hardcoded {@code proofValue}, but
 * JsonWebSignature2020 needs a {@code jws} field.
 */
class GaiaXJwsIssuer extends AbstractIssuer {
    GaiaXJwsIssuer(GaiaXJws2020SignatureSuite jws2020SignatureSuite, KeyPair keyPair) {
        super(jws2020SignatureSuite, keyPair, Multibase.BASE_64_URL);
    }

    @Override
    protected ExpandedVerifiable sign(final ModelVersion version, final JsonArray context, final JsonObject expanded,
                                      final ProofDraft draft, final DocumentLoader loader) throws SigningError, DocumentError {

        if (keyPair.privateKey() == null || keyPair.privateKey().length == 0) {
            throw new IllegalArgumentException("The private key is not provided, is null or an empty array.");
        }

        var object = expanded;

        var verifiable = Verifiable.of(version, object);

        // TODO do something with exceptions, unify
        if (verifiable.isCredential() && verifiable.asCredential().isExpired()) {
            throw new SigningError(SigningError.Code.Expired);
        }

        verifiable.validate();

        // add issuance date if missing
        if (verifiable.isCredential()
                && (verifiable.version() == null || ModelVersion.V11.equals(verifiable.version()))
                && verifiable.asCredential().issuanceDate() == null) {

            var issuanceDate = Instant.now().truncatedTo(ChronoUnit.SECONDS);

            object = Json.createObjectBuilder(object)
                    .add(VcVocab.ISSUANCE_DATE.uri(), issuanceDate.toString())
                    .build();
        }

        // remove proofs
        var unsigned = EmbeddedProof.removeProofs(object);

        // signature
        var signature = sign(context, unsigned, draft);

        var proofValue = Json.createValue(new String(signature));

        // signed proof
        var signedProof = Jws2020ProofDraft.signed(draft.unsigned(), proofValue);

        return new ExpandedVerifiable(EmbeddedProof.addProof(object, signedProof), context, loader);
    }

    @Override
    protected byte[] sign(JsonArray context, JsonObject document, ProofDraft draft) throws SigningError {
        var unsignedDraft = draft.unsigned();

        var ldSignature = new LinkedDataSignature(draft.cryptoSuite());

        return ldSignature.sign(document, keyPair.privateKey(), unsignedDraft);
    }
}

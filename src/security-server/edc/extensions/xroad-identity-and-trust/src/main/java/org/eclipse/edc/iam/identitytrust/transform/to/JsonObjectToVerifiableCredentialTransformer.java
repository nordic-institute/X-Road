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

package org.eclipse.edc.iam.identitytrust.transform.to;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.Builder;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_DESCRIPTION_PROPERTY;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_EXPIRATIONDATE_PROPERTY;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_ISSUANCEDATE_PROPERTY;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_ISSUER_PROPERTY;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_NAME_PROPERTY;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_PROOF_PROPERTY;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_STATUS_PROPERTY;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_SUBJECT_PROPERTY;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_VALIDFROM_PROPERTY;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential.VERIFIABLE_CREDENTIAL_VALIDUNTIL_PROPERTY;

/**
 * Transforms a JSON-LD structure into a {@link VerifiableCredential}.
 * Note that keeping a raw form of the JSON-LD for verification purposes is highly recommended.
 */
public class JsonObjectToVerifiableCredentialTransformer extends AbstractJsonLdTransformer<JsonObject, VerifiableCredential> {

    public JsonObjectToVerifiableCredentialTransformer() {
        super(JsonObject.class, VerifiableCredential.class);
    }

    @Override
    public @Nullable VerifiableCredential transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {

        var vcBuilder = Builder.newInstance();
        vcBuilder.id(nodeId(jsonObject));
        transformArrayOrObject(jsonObject.get(JsonLdKeywords.TYPE), Object.class, o -> vcBuilder.type(o.toString()), context);

        visitProperties(jsonObject, (s, jsonValue) -> transformProperties(s, jsonValue, vcBuilder, context));
        return vcBuilder.build();
    }

    private void transformProperties(String key, JsonValue jsonValue, Builder vcBuilder, TransformerContext context) {
        switch (key) {
            case VERIFIABLE_CREDENTIAL_DESCRIPTION_PROPERTY ->
                    vcBuilder.description(transformString(jsonValue, context));
            case VERIFIABLE_CREDENTIAL_ISSUER_PROPERTY -> vcBuilder.issuer(parseIssuer(jsonValue, context));
            case VERIFIABLE_CREDENTIAL_VALIDFROM_PROPERTY, VERIFIABLE_CREDENTIAL_ISSUANCEDATE_PROPERTY ->
                    vcBuilder.issuanceDate(parseDate(jsonValue, context));
            case VERIFIABLE_CREDENTIAL_VALIDUNTIL_PROPERTY, VERIFIABLE_CREDENTIAL_EXPIRATIONDATE_PROPERTY ->
                    vcBuilder.expirationDate(parseDate(jsonValue, context));
            case VERIFIABLE_CREDENTIAL_STATUS_PROPERTY ->
                    vcBuilder.credentialStatus(transformArray(jsonValue, CredentialStatus.class, context));
            case VERIFIABLE_CREDENTIAL_SUBJECT_PROPERTY ->
                    vcBuilder.credentialSubjects(transformArray(jsonValue, CredentialSubject.class, context));
            case VERIFIABLE_CREDENTIAL_NAME_PROPERTY -> vcBuilder.name(transformString(jsonValue, context));
            case VERIFIABLE_CREDENTIAL_PROOF_PROPERTY,
                    "https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#evidence",
                 "https://www.w3.org/2018/credentials#evidence" -> {
                //TODO verify that its required
                //noop
            }
            default ->
                    context.reportProblem("Unknown property: %s type: %s".formatted(key, jsonValue.getValueType().name()));
        }
    }

    private Instant parseDate(JsonValue jsonValue, TransformerContext context) {
        var str = transformString(jsonValue, context);
        return Instant.parse(str);
    }

    private Issuer parseIssuer(JsonValue jsonValue, TransformerContext context) {
        if (jsonValue.getValueType() == JsonValue.ValueType.STRING) {
            return new Issuer(transformString(jsonValue, context), Map.of());
        } else {
            // issuers can be objects, that MUST contain an ID, and optional other properties
            // an issuer is never an array with >1 elements
            JsonObject issuer;
            if (jsonValue.getValueType() == JsonValue.ValueType.ARRAY) {
                issuer = jsonValue.asJsonArray().get(0).asJsonObject();
            } else if (jsonValue.getValueType() == JsonValue.ValueType.OBJECT) {
                issuer = jsonValue.asJsonObject();
            } else {
                throw new IllegalArgumentException("Unknown issuer type, expected ARRAY or OBJECT, was %s"
                        .formatted(jsonValue.getValueType()));
            }
            return transformObject(issuer, Issuer.class, context);
        }
    }
}

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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiablePresentation;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonObjectToVerifiablePresentationTransformer extends AbstractJsonLdTransformer<JsonObject, VerifiablePresentation> {
    public JsonObjectToVerifiablePresentationTransformer() {
        super(JsonObject.class, VerifiablePresentation.class);
    }

    @Override
    public @Nullable VerifiablePresentation transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {
        var vcBuilder = VerifiablePresentation.Builder.newInstance();
        vcBuilder.id(nodeId(jsonObject));
        transformArrayOrObject(jsonObject.get(JsonLdKeywords.TYPE), Object.class, o -> vcBuilder.type(o.toString()), context);

        visitProperties(jsonObject, (s, jsonValue) -> transformProperties(s, jsonValue, vcBuilder, context));
        return vcBuilder.build();
    }

    private void transformProperties(String key, JsonValue jsonValue, VerifiablePresentation.Builder vpBuilder, TransformerContext context) {
        switch (key) {
            case VerifiablePresentation.VERIFIABLE_PRESENTATION_HOLDER_PROPERTY ->
                    vpBuilder.holder(transformString(jsonValue, context));
            case VerifiablePresentation.VERIFIABLE_PRESENTATION_VC_PROPERTY ->
                    transformCredential(jsonValue, vpBuilder, context);
            case VerifiablePresentation.VERIFIABLE_PRESENTATION_PROOF_PROPERTY -> {
                //noop
            }
            default ->
                    context.reportProblem("Unknown property: %s type: %s".formatted(key, jsonValue.getValueType().name()));
        }
    }

    /**
     * Credentials appear to be defined as "@graph", so that's what they're expanded to.
     */
    private void transformCredential(JsonValue jsonValue, VerifiablePresentation.Builder vpBuilder, TransformerContext context) {
        if (jsonValue instanceof JsonArray array) {
            array.forEach(content -> {
                if (content instanceof JsonObject) {
                    var credArray = ((JsonObject) content).getJsonArray(JsonLdKeywords.GRAPH);
                    transformArrayOrObject(credArray, VerifiableCredential.class, vpBuilder::credential, context);
                }
            });
        }
    }
}

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

package org.eclipse.edc.iam.identitytrust.transform.to;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.iam.identitytrust.spi.model.PresentationResponseMessage;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.credentialservice.PresentationSubmission;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Transforms a {@link JsonObject} into a {@link PresentationResponseMessage} object.
 */
public class JsonObjectToPresentationResponseMessageTransformer extends AbstractJsonLdTransformer<JsonObject, PresentationResponseMessage> {

    private final ObjectMapper mapper;

    public JsonObjectToPresentationResponseMessageTransformer(ObjectMapper mapper) {
        super(JsonObject.class, PresentationResponseMessage.class);
        this.mapper = mapper.copy().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    }

    @Override
    public @Nullable PresentationResponseMessage transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {
        var builder = PresentationResponseMessage.Builder.newinstance();
        visitProperties(jsonObject, (k, v) -> {
            switch (k) {
                case PresentationResponseMessage.PRESENTATION_RESPONSE_MESSAGE_PRESENTATION_SUBMISSION_PROPERTY ->
                        builder.presentationSubmission(readPresentationSubmission(v, context));
                case PresentationResponseMessage.PRESENTATION_RESPONSE_MESSAGE_PRESENTATION_PROPERTY ->
                        builder.presentation(readPresentation(v, context));
                default -> context.reportProblem("Unknown property '%s'".formatted(k));
            }
        });

        return builder.build();
    }

    private PresentationSubmission readPresentationSubmission(JsonValue v, TransformerContext context) {
        var rawJson = getRawJsonValue(v);
        try {
            return mapper.readValue(rawJson.toString(), PresentationSubmission.class);
        } catch (JsonProcessingException e) {
            context.reportProblem("Error reading JSON literal: %s".formatted(e.getMessage()));
            return null;
        }
    }


    private List<Object> readPresentation(JsonValue v, TransformerContext context) {
        var rawJson = getRawJsonValue(v);
        try {
            return mapper.readValue(rawJson.toString(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            context.reportProblem("Error reading JSON literal: %s".formatted(e.getMessage()));
            return null;
        }
    }

    private JsonValue getRawJsonValue(JsonValue v) {
        JsonObject jo;
        if (v.getValueType() == JsonValue.ValueType.ARRAY && !((JsonArray) v).isEmpty()) {
            jo = v.asJsonArray().getJsonObject(0);
        } else {
            jo = v.asJsonObject();
        }
        return jo.get(JsonLdKeywords.VALUE);
    }

}

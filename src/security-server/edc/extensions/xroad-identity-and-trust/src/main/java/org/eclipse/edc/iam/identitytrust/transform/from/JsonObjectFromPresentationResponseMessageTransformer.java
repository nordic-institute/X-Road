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

package org.eclipse.edc.iam.identitytrust.transform.from;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.iam.identitytrust.spi.model.PresentationResponseMessage;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.iam.identitytrust.spi.model.PresentationResponseMessage.PRESENTATION_RESPONSE_MESSAGE_PRESENTATION_PROPERTY;
import static org.eclipse.edc.iam.identitytrust.spi.model.PresentationResponseMessage.PRESENTATION_RESPONSE_MESSAGE_TYPE_PROPERTY;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;


/**
 * Transforms a {@link PresentationResponseMessage} into a {@link JsonObject} object.
 */
public class JsonObjectFromPresentationResponseMessageTransformer extends AbstractJsonLdTransformer<PresentationResponseMessage, JsonObject> {
    public JsonObjectFromPresentationResponseMessageTransformer() {
        super(PresentationResponseMessage.class, JsonObject.class);
    }

    @Override
    public @Nullable JsonObject transform(@NotNull PresentationResponseMessage responseMessage, @NotNull TransformerContext context) {
        // Presentation Submission not supported yet
        return Json.createObjectBuilder()
                .add(TYPE, PRESENTATION_RESPONSE_MESSAGE_TYPE_PROPERTY)
                .add(PRESENTATION_RESPONSE_MESSAGE_PRESENTATION_PROPERTY, createJson(responseMessage))
                .build();
    }

    private JsonObject createJson(PresentationResponseMessage responseMessage) {
        var jo = Json.createObjectBuilder();
        jo.add(JsonLdKeywords.VALUE, Json.createArrayBuilder(responseMessage.getPresentation()).build());
        jo.add(JsonLdKeywords.TYPE, JsonLdKeywords.JSON);
        return jo.build();
    }

}

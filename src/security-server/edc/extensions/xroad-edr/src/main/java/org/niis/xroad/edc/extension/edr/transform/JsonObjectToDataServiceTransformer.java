/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.niis.xroad.edc.extension.edr.transform;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.catalog.spi.DataService;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_ENDPOINT_URL_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_TERMS_ATTRIBUTE;

/**
 * Converts from a DCAT data service as a {@link JsonObject} in JSON-LD expanded form to a {@link DataService}.
 */
public class JsonObjectToDataServiceTransformer extends AbstractJsonLdTransformer<JsonObject, DataService> {

    public JsonObjectToDataServiceTransformer() {
        super(JsonObject.class, DataService.class);
    }

    @Override
    public @Nullable DataService transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var builder = DataService.Builder.newInstance();

        builder.id(nodeId(object));
        visitProperties(object, (key, value) -> transformProperties(key, value, builder, context));

        return builderResult(builder::build, context);
    }

    private void transformProperties(String key, JsonValue value, DataService.Builder builder, TransformerContext context) {
        if (DCT_TERMS_ATTRIBUTE.equals(key)) {
            transformString(value, builder::terms, context);
        } else if (DCT_ENDPOINT_URL_ATTRIBUTE.equals(key)) {
            transformString(value, builder::endpointUrl, context);
        }
    }
}

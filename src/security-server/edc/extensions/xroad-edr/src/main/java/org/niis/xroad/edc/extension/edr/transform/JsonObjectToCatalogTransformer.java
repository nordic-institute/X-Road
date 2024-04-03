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
import org.eclipse.edc.catalog.spi.Catalog;
import org.eclipse.edc.catalog.spi.DataService;
import org.eclipse.edc.catalog.spi.Dataset;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DATASET_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DATA_SERVICE_ATTRIBUTE;

/**
 * Converts from a DCAT catalog as a {@link JsonObject} in JSON-LD expanded form to a {@link Catalog}.
 */
public class JsonObjectToCatalogTransformer extends AbstractJsonLdTransformer<JsonObject, Catalog> {

    public JsonObjectToCatalogTransformer() {
        super(JsonObject.class, Catalog.class);
    }

    @Override
    public @Nullable Catalog transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var builder = Catalog.Builder.newInstance();

        builder.id(nodeId(object));
        visitProperties(object, (key, value) -> transformProperties(key, value, builder, context));

        return builderResult(builder::build, context);
    }

    private void transformProperties(String key, JsonValue value, Catalog.Builder builder, TransformerContext context) {
        if (DCAT_DATASET_ATTRIBUTE.equals(key)) {
            transformArrayOrObject(value, Dataset.class, builder::dataset, context);
        } else if (DCAT_DATA_SERVICE_ATTRIBUTE.equals(key)) {
            transformArrayOrObject(value, DataService.class, builder::dataService, context);
        } else {
            builder.property(key, transformGenericProperty(value, context));
        }
    }
}

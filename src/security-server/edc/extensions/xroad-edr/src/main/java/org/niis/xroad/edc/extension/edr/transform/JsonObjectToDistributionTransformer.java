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
import org.eclipse.edc.catalog.spi.Distribution;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_ACCESS_SERVICE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_FORMAT_ATTRIBUTE;

/**
 * Converts from a DCAT distribution as a {@link JsonObject} in JSON-LD expanded form to a {@link Distribution}.
 */
public class JsonObjectToDistributionTransformer extends AbstractJsonLdTransformer<JsonObject, Distribution> {

    public JsonObjectToDistributionTransformer() {
        super(JsonObject.class, Distribution.class);
    }

    @Override
    public @Nullable Distribution transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var builder = Distribution.Builder.newInstance();
        visitProperties(object, (key, value) -> transformProperties(key, value, builder, context));
        return builderResult(builder::build, context);
    }

    private void transformProperties(String key, JsonValue value, Distribution.Builder builder, TransformerContext context) {
        if (DCAT_ACCESS_SERVICE_ATTRIBUTE.equals(key)) {
            var dataServiceBuilder = DataService.Builder.newInstance();
            transformString(value, dataServiceBuilder::id, context);
            builder.dataService(dataServiceBuilder.build());
        } else if (DCT_FORMAT_ATTRIBUTE.equals(key)) {
            transformString(value, builder::format, context);
        }
    }
}

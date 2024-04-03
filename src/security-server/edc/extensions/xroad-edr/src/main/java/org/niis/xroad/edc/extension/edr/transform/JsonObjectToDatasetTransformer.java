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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.catalog.spi.Dataset;
import org.eclipse.edc.catalog.spi.Distribution;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jakarta.json.JsonValue.ValueType.ARRAY;
import static jakarta.json.JsonValue.ValueType.OBJECT;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DATASET_TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DISTRIBUTION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_ATTRIBUTE;

/**
 * Converts from a DCAT dataset as a {@link JsonObject} in JSON-LD expanded form to a {@link Dataset}.
 */
public class JsonObjectToDatasetTransformer extends AbstractJsonLdTransformer<JsonObject, Dataset> {

    public JsonObjectToDatasetTransformer() {
        super(JsonObject.class, Dataset.class);
    }

    @Override
    public @Nullable Dataset transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var builder = Dataset.Builder.newInstance();

        builder.id(nodeId(object));
        visitProperties(object, (key, value) -> transformProperties(key, value, builder, context));

        return builderResult(builder::build, context);
    }

    private void transformProperties(String key, JsonValue value, Dataset.Builder builder, TransformerContext context) {
        switch (key) {
            case ODRL_POLICY_ATTRIBUTE -> transformPolicies(value, builder, context);
            case DCAT_DISTRIBUTION_ATTRIBUTE ->
                    transformArrayOrObject(value, Distribution.class, builder::distribution, context);
            default -> builder.property(key, transformGenericProperty(value, context));
        }
    }

    private void transformPolicies(JsonValue value, Dataset.Builder builder, TransformerContext context) {
        if (value instanceof JsonObject object) {
            var id = nodeId(object);
            var policy = context.transform(object, Policy.class);
            builder.offer(id, policy);
        } else if (value instanceof JsonArray array) {
            array.forEach(entry -> transformPolicies(entry, builder, context));
        } else {
            context.problem()
                    .unexpectedType()
                    .type(DCAT_DATASET_TYPE)
                    .property(ODRL_POLICY_ATTRIBUTE)
                    .actual(value == null ? "null" : value.getValueType().toString())
                    .expected(OBJECT)
                    .expected(ARRAY)
                    .report();
        }
    }
}

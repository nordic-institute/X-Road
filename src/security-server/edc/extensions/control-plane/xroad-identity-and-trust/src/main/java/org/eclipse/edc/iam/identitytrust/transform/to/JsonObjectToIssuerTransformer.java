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
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class JsonObjectToIssuerTransformer extends AbstractJsonLdTransformer<JsonObject, Issuer> {
    public JsonObjectToIssuerTransformer() {
        super(JsonObject.class, Issuer.class);
    }

    @Override
    public @Nullable Issuer transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {
        var id = nodeId(jsonObject);
        var props = new HashMap<String, Object>();
        visitProperties(jsonObject, (key, jsonValue) -> {
            if (!JsonLdKeywords.ID.equals(key)) {
                var res = transformGenericProperty(jsonValue, context);
                props.put(key, res);
            }
        });
        return new Issuer(id, props);
    }
}

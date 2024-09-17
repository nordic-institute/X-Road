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
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

import static org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus.CREDENTIAL_STATUS_TYPE_PROPERTY;

public class JsonObjectToCredentialStatusTransformer extends AbstractJsonLdTransformer<JsonObject, CredentialStatus> {
    public JsonObjectToCredentialStatusTransformer() {
        super(JsonObject.class, CredentialStatus.class);
    }

    @Override
    public @Nullable CredentialStatus transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {


        var props = new HashMap<String, Object>();
        var id = nodeId(jsonObject);
        var type = transformString(jsonObject.get(CREDENTIAL_STATUS_TYPE_PROPERTY), context);
        visitProperties(jsonObject, (s, jsonValue) -> props.put(s, transformGenericProperty(jsonValue, context)));

        return new CredentialStatus(id, type, props);
    }
}

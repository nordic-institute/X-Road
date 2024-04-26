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
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonObjectToCredentialSubjectTransformer extends AbstractJsonLdTransformer<JsonObject, CredentialSubject> {
    public JsonObjectToCredentialSubjectTransformer() {
        super(JsonObject.class, CredentialSubject.class);
    }

    @Override
    public @Nullable CredentialSubject transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {
        var builder = CredentialSubject.Builder.newInstance();

        builder.id(nodeId(jsonObject));
        visitProperties(jsonObject, (s, jsonValue) -> {
            if (s.equals(CredentialSubject.CREDENTIAL_SUBJECT_ID_PROPERTY)) {
                builder.id(transformString(jsonValue, context));
            } else {
                builder.claim(s, transformGenericProperty(jsonValue, context));
            }
        });

        return builder.build();
    }
}

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

package org.eclipse.edc.iam.identitytrust.transform.from;

import jakarta.json.JsonObject;
import org.eclipse.edc.iam.identitytrust.spi.model.PresentationQueryMessage;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonObjectFromPresentationQueryTransformer extends AbstractJsonLdTransformer<PresentationQueryMessage, JsonObject> {

    public JsonObjectFromPresentationQueryTransformer() {
        super(PresentationQueryMessage.class, JsonObject.class);
    }

    @Override
    public @Nullable JsonObject transform(@NotNull PresentationQueryMessage presentationQueryMessage, @NotNull TransformerContext context) {
        return null;
    }
}

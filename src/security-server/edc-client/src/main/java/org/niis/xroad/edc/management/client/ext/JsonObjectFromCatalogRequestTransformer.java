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

package org.niis.xroad.edc.management.client.ext;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.eclipse.edc.catalog.spi.Catalog;
import org.eclipse.edc.catalog.spi.CatalogRequest;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.catalog.spi.CatalogRequest.CATALOG_REQUEST_COUNTER_PARTY_ADDRESS;
import static org.eclipse.edc.catalog.spi.CatalogRequest.CATALOG_REQUEST_PROTOCOL;

/**
 * Converts from a {@link Catalog} to a DCAT catalog as a {@link JsonObject} in JSON-LD expanded form.
 */
public class JsonObjectFromCatalogRequestTransformer extends AbstractJsonLdTransformer<CatalogRequest, JsonObject> {
    private final JsonBuilderFactory jsonFactory;
    private final ObjectMapper mapper;

    public JsonObjectFromCatalogRequestTransformer(JsonBuilderFactory jsonFactory, ObjectMapper mapper) {
        super(CatalogRequest.class, JsonObject.class);
        this.jsonFactory = jsonFactory;
        this.mapper = mapper;
    }

    @Override
    public @Nullable JsonObject transform(@NotNull CatalogRequest catalog, @NotNull TransformerContext context) {
        var objectBuilder = jsonFactory.createObjectBuilder();
//        objectBuilder.add(ID, catalog.getId());
//        objectBuilder.add(TYPE, DCAT_CATALOG_TYPE);
        objectBuilder.add(CATALOG_REQUEST_COUNTER_PARTY_ADDRESS, catalog.getCounterPartyAddress());
        objectBuilder.add(CATALOG_REQUEST_PROTOCOL, catalog.getProtocol());

        //TODO CATALOG_REQUEST_QUERY_SPEC missing

        return objectBuilder.build();
    }
}

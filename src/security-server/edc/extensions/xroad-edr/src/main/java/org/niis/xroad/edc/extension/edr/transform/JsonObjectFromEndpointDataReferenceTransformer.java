/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.edc.extension.edr.transform;

import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.spi.types.domain.edr.EndpointDataReference;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;

public class JsonObjectFromEndpointDataReferenceTransformer extends AbstractJsonLdTransformer<EndpointDataReference, JsonObject> {

    public JsonObjectFromEndpointDataReferenceTransformer() {
        super(EndpointDataReference.class, JsonObject.class);
    }

    @Override
    public @Nullable JsonObject transform(@NotNull EndpointDataReference dto, @NotNull TransformerContext transformerContext) {

        return createObjectBuilder()
                .add(JsonLdKeywords.TYPE, EDC_NAMESPACE + EndpointDataReference.EDR_SIMPLE_TYPE)
                .add(EndpointDataReference.ID, dto.getId())
                .add(EndpointDataReference.CONTRACT_ID, dto.getContractId())
                .add(EndpointDataReference.AUTH_CODE, dto.getAuthCode())
                .add(EndpointDataReference.AUTH_KEY, dto.getAuthKey())
                .add(EndpointDataReference.ENDPOINT, dto.getEndpoint())
                .build();
    }

}

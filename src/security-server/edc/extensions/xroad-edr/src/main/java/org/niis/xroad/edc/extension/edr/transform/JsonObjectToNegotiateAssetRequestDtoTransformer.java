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
import jakarta.json.JsonValue;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.niis.xroad.edc.extension.edr.dto.NegotiateAssetRequestDto;

import static org.niis.xroad.edc.extension.edr.dto.NegotiateAssetRequestDto.XRD_EDR_REQUEST_ASSET_ID;
import static org.niis.xroad.edc.extension.edr.dto.NegotiateAssetRequestDto.XRD_EDR_REQUEST_DTO_CLIENT_ID;
import static org.niis.xroad.edc.extension.edr.dto.NegotiateAssetRequestDto.XRD_EDR_REQUEST_DTO_COUNTERPARTY_ADDRESS;
import static org.niis.xroad.edc.extension.edr.dto.NegotiateAssetRequestDto.XRD_EDR_REQUEST_DTO_TYPE;

public class JsonObjectToNegotiateAssetRequestDtoTransformer extends AbstractJsonLdTransformer<JsonObject, NegotiateAssetRequestDto> {

    public JsonObjectToNegotiateAssetRequestDtoTransformer() {
        super(JsonObject.class, NegotiateAssetRequestDto.class);
    }

    @Override
    public @Nullable NegotiateAssetRequestDto transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {
        var builder = NegotiateAssetRequestDto.Builder.newInstance();
        visitProperties(jsonObject, (k, v) -> setProperties(k, v, builder, context));
        return builder.build();
    }

    private void setProperties(String key, JsonValue value, NegotiateAssetRequestDto.Builder builder, TransformerContext context) {
        switch (key) {
            case XRD_EDR_REQUEST_DTO_CLIENT_ID -> transformString(value, builder::clientId, context);
            case XRD_EDR_REQUEST_ASSET_ID -> transformString(value, builder::assetId, context);
            case XRD_EDR_REQUEST_DTO_COUNTERPARTY_ADDRESS -> transformString(value, builder::counterPartyAddress, context);
            default -> context.problem()
                    .unexpectedType()
                    .type(XRD_EDR_REQUEST_DTO_TYPE)
                    .property(key)
                    .actual(key)
                    .expected(XRD_EDR_REQUEST_DTO_CLIENT_ID)
                    .expected(XRD_EDR_REQUEST_ASSET_ID)
                    .expected(XRD_EDR_REQUEST_DTO_COUNTERPARTY_ADDRESS)
                    .report();
        }
    }
}
